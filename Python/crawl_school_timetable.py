from selenium import webdriver
from selenium.webdriver.edge.options import Options as EdgeOptions
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from bs4 import BeautifulSoup
from io import StringIO
from pathlib import Path

import pandas as pd
import time
import re
import os
import urllib.parse
import urllib.request


# =========================
# 基础配置
# =========================

LOGIN_URL = "https://ehall.hniu.cn/new/index.html?browser=no"

CLASS_PAGE_URL = "https://jw.hniu.cn/jsxsd/kbcx/kbxx_xzb"

RESULT_URL = "https://jw.hniu.cn/jsxsd/kbcx/kbxx_xzb_ifr"

USERNAME = os.environ.get("TIMETABLE_USERNAME", "").strip()
PASSWORD = os.environ.get("TIMETABLE_PASSWORD", "").strip()
HEADLESS = os.environ.get("TIMETABLE_HEADLESS", "true").strip().lower() != "false"

# 导出文件夹：当前 Python 文件同目录下的“课表导出结果”
OUTPUT_DIR = Path(__file__).resolve().parent / "课表导出结果"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# =========================
# 工具函数
# =========================

def clean_text(text):
    if text is None:
        return ""
    text = str(text)
    text = text.replace("\xa0", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def add_utf8_meta(html):
    if not html:
        return html

    if "charset" in html.lower():
        return html

    return re.sub(
        r"(<head[^>]*>)",
        r'\1\n<meta charset="UTF-8">',
        html,
        count=1,
        flags=re.IGNORECASE
    )


def save_html(filename, html):
    file_path = OUTPUT_DIR / filename

    html = add_utf8_meta(html)

    with open(file_path, "w", encoding="utf-8", newline="") as f:
        f.write(html)

    print("已保存：", file_path)


def save_text(filename, html):
    file_path = OUTPUT_DIR / filename

    soup = BeautifulSoup(html, "lxml")
    text = soup.get_text("\n")

    lines = []
    for line in text.splitlines():
        line = clean_text(line)
        if line:
            lines.append(line)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print("已保存：", file_path)


def js_click(driver, element):
    """
    用 JS 点击，解决 element not interactable
    """
    driver.execute_script("arguments[0].click();", element)


def wait_element_by_id(driver, element_id, timeout=20):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((By.ID, element_id))
    )


def wait_element_by_xpath(driver, xpath, timeout=20):
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((By.XPATH, xpath))
    )


def wait_input_by_id(driver, element_id, value, timeout=20):
    element = wait_element_by_id(driver, element_id, timeout)
    element.clear()
    element.send_keys(value)
    return element


def click_visible_element_by_id(driver, element_id, timeout=20):
    """
    页面里可能有多个同 ID 元素，有些是隐藏的。
    这里找可见元素再点击。
    """
    WebDriverWait(driver, timeout).until(
        lambda d: len(d.find_elements(By.ID, element_id)) > 0
    )

    elements = driver.find_elements(By.ID, element_id)

    for element in elements:
        if element.is_displayed():
            driver.execute_script("arguments[0].scrollIntoView(true);", element)
            time.sleep(0.5)
            js_click(driver, element)
            return True

    raise Exception(f"没有找到可见元素：{element_id}")


def switch_to_latest_window(driver):
    """
    切换到最新打开的窗口 / 标签页
    """
    time.sleep(2)

    handles = driver.window_handles
    print("当前窗口数量：", len(handles))

    driver.switch_to.window(handles[-1])

    print("已切换到最新窗口")
    print("当前地址：", driver.current_url)
    print("当前标题：", driver.title)


# =========================
# Excel 导出处理
# =========================

def flatten_columns(df):
    """
    处理 pandas 读取 HTML 合并表头后产生的 MultiIndex 多级表头
    """
    if isinstance(df.columns, pd.MultiIndex):
        new_columns = []

        for col in df.columns:
            parts = []

            for item in col:
                item = clean_text(str(item))

                if not item:
                    continue

                if item.lower() == "nan":
                    continue

                if item.startswith("Unnamed"):
                    continue

                parts.append(item)

            if parts:
                new_columns.append("_".join(parts))
            else:
                new_columns.append("列")

        df.columns = new_columns

    else:
        df.columns = [clean_text(str(col)) for col in df.columns]

    # 防止列名重复
    seen = {}
    final_columns = []

    for col in df.columns:
        if col not in seen:
            seen[col] = 1
            final_columns.append(col)
        else:
            seen[col] += 1
            final_columns.append(f"{col}_{seen[col]}")

    df.columns = final_columns

    return df


def clean_excel_illegal_chars(value):
    """
    清理 Excel 不支持的非法字符
    """
    if isinstance(value, str):
        value = re.sub(r"[\x00-\x08\x0B-\x0C\x0E-\x1F]", "", value)

    return value


def export_tables(html, output_excel):
    """
    把返回 HTML 里的所有表格导出到指定文件夹中的 Excel 和 CSV
    """
    try:
        tables = pd.read_html(StringIO(html))
    except Exception as e:
        print("没有识别到 HTML 表格：", e)
        return False

    if not tables:
        print("没有识别到任何表格。")
        return False

    print("识别到表格数量：", len(tables))

    output_excel_path = OUTPUT_DIR / output_excel

    with pd.ExcelWriter(output_excel_path, engine="openpyxl") as writer:
        for i, df in enumerate(tables, start=1):
            print(f"正在处理 table_{i}：{df.shape[0]} 行，{df.shape[1]} 列")

            # 把多级表头转成普通表头
            df = flatten_columns(df)

            # 清理 Excel 非法字符
            try:
                df = df.map(clean_excel_illegal_chars)
            except AttributeError:
                df = df.applymap(clean_excel_illegal_chars)

            sheet_name = f"table_{i}"
            df.to_excel(writer, sheet_name=sheet_name, index=False)

            csv_path = OUTPUT_DIR / f"班级课表大表结果_{i}.csv"
            df.to_csv(csv_path, index=False, encoding="utf-8-sig")

            print(f"{sheet_name}：{df.shape[0]} 行，{df.shape[1]} 列")
            print("已保存：", csv_path)

    print("导出完成：", output_excel_path)
    return True


# =========================
# 请求真实大表接口
# =========================

def build_big_table_payload():
    return {
        "xnxq01id": "2025-2026-2",
        "kbjcmsid": "67FB3A89FDC146ADA865DCC81B9EC143",

        # 下面这些为空，表示不筛选，尽量查大范围
        "skyx": "",
        "sknj": "",
        "skzy": "",
        "skbjid": "",
        "skbj": "",
        "zc1": "",
        "zc2": "",
        "skxq1": "",
        "skxq2": "",
        "jc1": "",
        "jc2": "",
    }


def decode_response_body(response):
    content_type = response.headers.get("Content-Type", "")
    charset_match = re.search(r"charset=([\w-]+)", content_type, re.IGNORECASE)
    charset = charset_match.group(1) if charset_match else "utf-8"
    body = response.read()
    try:
        return body.decode(charset, errors="replace")
    except LookupError:
        return body.decode("utf-8", errors="replace")


def fetch_big_table_by_http(driver):
    """
    使用浏览器当前 Cookie 由 Python 直接请求真实接口。
    这比在页面里执行 fetch 更稳，能避开浏览器跨域/跳转限制。
    """

    payload = build_big_table_payload()
    body = urllib.parse.urlencode(payload).encode("utf-8")
    cookies = driver.get_cookies()
    cookie_header = "; ".join(
        f"{cookie.get('name')}={cookie.get('value')}"
        for cookie in cookies
        if cookie.get("name") and cookie.get("value") is not None
    )
    user_agent = driver.execute_script("return navigator.userAgent") or "Mozilla/5.0"

    request = urllib.request.Request(
        RESULT_URL,
        data=body,
        headers={
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Cookie": cookie_header,
            "Origin": "https://jw.hniu.cn",
            "Referer": CLASS_PAGE_URL,
            "User-Agent": user_agent,
            "X-Requested-With": "XMLHttpRequest",
        },
        method="POST",
    )

    print("正在用后端 HTTP 请求真实接口：", RESULT_URL)
    with urllib.request.urlopen(request, timeout=120) as response:
        html = decode_response_body(response)

    if "统一身份认证" in html or "登录" in BeautifulSoup(html, "lxml").get_text(" "):
        print("HTTP 请求结果疑似登录页，将回退到浏览器 fetch。")
        return ""

    return html


def fetch_big_table_by_browser(driver):
    """
    直接在浏览器当前登录环境中执行 fetch。
    浏览器会自动带上登录 Cookie。
    """

    payload = build_big_table_payload()

    js_code = """
    const url = arguments[0];
    const payload = arguments[1];
    const callback = arguments[2];

    const params = new URLSearchParams();

    for (const key in payload) {
        params.append(key, payload[key]);
    }

    fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: params.toString(),
        credentials: "include"
    })
    .then(response => response.text())
    .then(text => callback(text))
    .catch(error => callback("FETCH_ERROR:" + error.toString()));
    """

    print("正在请求真实接口：", RESULT_URL)

    driver.set_script_timeout(120)

    html = driver.execute_async_script(js_code, RESULT_URL, payload)

    if html.startswith("FETCH_ERROR:"):
        raise Exception(html)

    return html


def fetch_big_table(driver):
    try:
        html = fetch_big_table_by_http(driver)
        if html:
            return html
    except Exception as e:
        print("后端 HTTP 请求真实接口失败，回退到浏览器 fetch：", e)

    return fetch_big_table_by_browser(driver)


# =========================
# 登录门户并进入教务系统
# =========================

def login_portal_and_enter_jw(driver):
    """
    登录门户并进入教务系统
    """
    driver.get(LOGIN_URL)
    if not HEADLESS:
        driver.maximize_window()

    time.sleep(3)

    print("当前地址：", driver.current_url)
    print("当前标题：", driver.title)

    # 1. 点击门户登录按钮
    try:
        click_visible_element_by_id(driver, "ampLoginBtn", timeout=20)
        print("已点击门户登录按钮")
        time.sleep(2)
    except Exception as e:
        print("登录按钮点击失败，可能已经登录或页面结构变化：", e)

    # 2. 输入账号密码并登录
    try:
        wait_input_by_id(driver, "username", USERNAME, timeout=15)
        wait_input_by_id(driver, "password", PASSWORD, timeout=15)

        login_btn = wait_element_by_xpath(
            driver,
            '//*[@id="casLoginForm"]/p[4]/button',
            timeout=20
        )
        js_click(driver, login_btn)

        print("已提交登录")
        time.sleep(5)

    except Exception as e:
        print("账号密码登录步骤失败，可能已经登录：", e)

    # 3. 点击教务系统应用
    try:
        app = wait_element_by_xpath(
            driver,
            '//*[@id="cardMyFavoriteContent"]/div/widget-app-item/div/div/div[2]/div',
            timeout=30
        )
        js_click(driver, app)

        print("已点击教务系统应用")
        time.sleep(2)

    except Exception as e:
        print("点击教务系统应用失败：", e)
        if HEADLESS:
            raise Exception("后台无头模式下无法手动点击教务系统应用，请检查门户页面结构或账号状态") from e
        print("请你手动点击教务系统应用。")
        input("点击完成后，回到这里按回车继续：")

    # 4. 点击进入教务系统
    try:
        enter_btn = wait_element_by_xpath(
            driver,
            '//*[@id="ampDetailEnter"]',
            timeout=30
        )
        js_click(driver, enter_btn)

        print("已点击进入教务系统")
        time.sleep(5)

    except Exception as e:
        print("点击进入教务系统失败：", e)
        if HEADLESS:
            raise Exception("后台无头模式下无法手动点击进入教务系统，请检查门户页面结构或账号状态") from e
        print("请你手动点击进入教务系统。")
        input("进入教务系统后，回到这里按回车继续：")

    # 5. 教务系统会打开新窗口，所以必须切换
    switch_to_latest_window(driver)

    # 6. 确认进入教务系统
    print("进入教务系统后的地址：", driver.current_url)
    print("进入教务系统后的标题：", driver.title)

    if "jw.hniu.cn" not in driver.current_url:
        print("当前可能还没有进入教务系统。")
        if HEADLESS:
            raise Exception("后台无头模式下未进入教务系统，请检查账号密码或门户跳转")
        input("请你手动进入教务系统后，回到这里按回车继续：")
        switch_to_latest_window(driver)


# =========================
# 主程序
# =========================

def create_driver():
    options = EdgeOptions()
    options.add_argument("--window-size=1440,1000")
    options.add_argument("--disable-gpu")

    if HEADLESS:
        options.add_argument("--headless=new")
        options.add_argument("--disable-extensions")
        options.add_argument("--disable-popup-blocking")
        options.add_argument("--log-level=3")

    return webdriver.Edge(options=options)


def main():
    if not USERNAME or not PASSWORD:
        raise RuntimeError("缺少教务系统账号或密码，请先在课表抓取页面保存账号配置")

    driver = create_driver()

    try:
        login_portal_and_enter_jw(driver)

        print("正在打开班级课表查询页面...")

        # 这里不需要点击左侧菜单，直接打开真实班级课表查询页面
        driver.get(CLASS_PAGE_URL)

        time.sleep(3)

        print("当前地址：", driver.current_url)
        print("当前标题：", driver.title)

        page_html = driver.page_source
        save_html("班级课表查询页面.html", page_html)

        # 请求真实大表接口
        result_html = fetch_big_table(driver)

        save_html("班级课表大表结果.html", result_html)
        save_text("班级课表大表结果.txt", result_html)

        ok = export_tables(result_html, "班级课表大表结果.xlsx")

        if not ok:
            print("没有导出成功。")
            print("请打开：", OUTPUT_DIR / "班级课表大表结果.html")
            print("如果里面是登录页，说明还没有真正进入教务系统。")
            print("如果里面是空白，说明接口还需要其他参数。")
        else:
            print("全部完成。")
            print("所有文件已导出到：", OUTPUT_DIR)

    finally:
        # 不关闭浏览器，方便你检查
        driver.quit()


if __name__ == "__main__":
    main()
