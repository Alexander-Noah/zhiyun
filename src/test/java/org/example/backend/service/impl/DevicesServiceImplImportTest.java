package org.example.backend.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicesServiceImplImportTest {
    @Test
    void normalizeDeviceBindsLabByLabId() throws Exception {
        Object service = newDevicesServiceWithLabs(List.of(newLab(7, "软件部署实训室", "2-412")));
        Object device = newDevice();
        invokeSetter(device, "setDeviceName", String.class, "教师机");
        invokeSetter(device, "setLabId", Long.class, 7L);

        invokeNormalizeDevice(service, device);

        assertEquals(7L, invokeObject(device, "getLabId"));
        assertEquals("软件部署实训室", invokeString(device, "getLabName"));
        assertEquals("2-412", invokeString(device, "getLocation"));
    }

    @Test
    void normalizeDeviceBindsLabByLocationWhenLabIdMissing() throws Exception {
        Object service = newDevicesServiceWithLabs(List.of(newLab(8, "WEB后端开发实训室", "2-209")));
        Object device = newDevice();
        invokeSetter(device, "setDeviceName", String.class, "2-209 交换机");
        invokeSetter(device, "setLocation", String.class, "2-209");

        invokeNormalizeDevice(service, device);

        assertEquals(8L, invokeObject(device, "getLabId"));
        assertEquals("WEB后端开发实训室", invokeString(device, "getLabName"));
        assertEquals("2-209", invokeString(device, "getLocation"));
    }

    @Test
    void importRowAppliesSpecificationOnlyToComputerOrMatchingDeviceName() throws Exception {
        Object service = newDevicesService();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("实验室名称", "智慧实验室");
        row.put("地点", "A101");
        row.put("主要设备名称", String.join(" ",
                "台式电脑：联想启天M410 1台",
                "交换机：TP-LINK 24口 1台",
                "交换机：TP-LINK 48口 1台",
                "投影仪：卡西欧FC330 1台",
                "空调：KFR-72LW/DY-X(E2) 1台"));
        row.put("设备名称", "联想启天M410");
        row.put("主要功能和技术要求", "CPU i5，内存16G，硬盘1T");

        List<Object> devices = invokeMapImportRow(service, row);
        Map<String, Object> devicesByName = devices.stream()
                .collect(Collectors.toMap(device -> invokeString(device, "getDeviceName"), device -> device));

        assertEquals("CPU i5，内存16G，硬盘1T", invokeString(devicesByName.get("联想启天M410"), "getSpecs"));
        assertEquals("", invokeString(devicesByName.get("TP-LINK 24口"), "getSpecs"));
        assertEquals("", invokeString(devicesByName.get("TP-LINK 48口"), "getSpecs"));
        assertEquals("", invokeString(devicesByName.get("卡西欧FC330"), "getSpecs"));
        assertEquals("", invokeString(devicesByName.get("KFR-72LW/DY-X(E2)"), "getSpecs"));

        assertEquals("计算终端", invokeString(devicesByName.get("联想启天M410"), "getCategory"));
        assertEquals("网络设备", invokeString(devicesByName.get("TP-LINK 24口"), "getCategory"));
        assertEquals("网络设备", invokeString(devicesByName.get("TP-LINK 48口"), "getCategory"));
        assertEquals("显示设备", invokeString(devicesByName.get("卡西欧FC330"), "getCategory"));
        assertEquals("环境设备", invokeString(devicesByName.get("KFR-72LW/DY-X(E2)"), "getCategory"));
    }

    @Test
    void importRowAllowsSpecificationForNonComputerDeviceWhenDeviceNameMatchesColumnJ() throws Exception {
        Object service = newDevicesService();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("主要设备名称", "投影仪：卡西欧FC330 1台 空调：KFR-72LW/DY-X(E2) 1台");
        row.put("设备名称", "卡西欧FC330");
        row.put("主要功能和技术要求", "3300流明，短焦投影");

        List<Object> devices = invokeMapImportRow(service, row);
        Map<String, Object> devicesByName = devices.stream()
                .collect(Collectors.toMap(device -> invokeString(device, "getDeviceName"), device -> device));

        assertEquals("3300流明，短焦投影", invokeString(devicesByName.get("卡西欧FC330"), "getSpecs"));
        assertEquals("", invokeString(devicesByName.get("KFR-72LW/DY-X(E2)"), "getSpecs"));
    }

    @SuppressWarnings("unchecked")
    private List<Object> invokeMapImportRow(Object service, Map<String, Object> row) throws Exception {
        Method method = service.getClass().getDeclaredMethod("mapImportRow", Map.class);
        method.setAccessible(true);
        return (List<Object>) method.invoke(service, row);
    }

    @Test
    void importDevicesRejectsFilesLargerThanLimit() throws Exception {
        Object service = newDevicesService();
        MultipartFile file = multipartFile("devices.csv", 10L * 1024 * 1024 + 1, "name\ncomputer".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> invokeImportDevices(service, file));

        assertTrue(error.getMessage().contains("10MB"));
    }

    @Test
    void importDevicesRejectsUnsupportedExtension() throws Exception {
        Object service = newDevicesService();
        MultipartFile file = multipartFile("devices.txt", 12, "name\ncomputer".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> invokeImportDevices(service, file));

        assertTrue(error.getMessage().contains("CSV") || error.getMessage().contains("xlsx"));
    }

    @Test
    void readZipEntriesRejectsOversizedEntry() throws Exception {
        Object service = newDevicesService();
        Method method = service.getClass().getDeclaredMethod("readZipEntries", InputStream.class);
        method.setAccessible(true);
        byte[] zipBytes = zipWithEntry("xl/sharedStrings.xml", new byte[5 * 1024 * 1024 + 1]);

        Exception error = assertThrows(Exception.class, () -> method.invoke(service, new ByteArrayInputStream(zipBytes)));

        Throwable cause = error.getCause() == null ? error : error.getCause();
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(cause.getMessage().contains("too large"));
    }

    private Object invokeImportDevices(Object service, MultipartFile file) throws Exception {
        try {
            Method method = service.getClass().getMethod("importDevices", MultipartFile.class);
            return method.invoke(service, file);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private Object newDevicesService() throws Exception {
        return newDevicesServiceWithLabs(List.of());
    }

    private MultipartFile multipartFile(String filename, long declaredSize, byte[] content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public String getContentType() {
                return "application/octet-stream";
            }

            @Override
            public boolean isEmpty() {
                return declaredSize == 0;
            }

            @Override
            public long getSize() {
                return declaredSize;
            }

            @Override
            public byte[] getBytes() {
                return content;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                throw new IOException("not implemented");
            }
        };
    }

    private byte[] zipWithEntry(String name, byte[] content) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private Object newDevicesServiceWithLabs(List<Object> labs) throws Exception {
        Class<?> serviceClass = Class.forName("org.example.backend.service.impl.DevicesServiceImpl");
        Constructor<?> constructor = serviceClass.getConstructors()[0];
        Object[] args = new Object[constructor.getParameterCount()];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if ("org.example.backend.mapper.LabMapper".equals(parameterTypes[index].getName())) {
                args[index] = fakeLabMapper(parameterTypes[index], labs);
            }
        }
        return constructor.newInstance(args);
    }

    private Object fakeLabMapper(Class<?> mapperClass, List<Object> labs) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getLabs".equals(method.getName())) {
                return labs;
            }
            if ("getLabById".equals(method.getName())) {
                Integer id = args == null || args.length == 0 ? null : (Integer) args[0];
                return labs.stream().filter(lab -> invokeObject(lab, "getId").equals(id)).findFirst().orElse(null);
            }
            return null;
        };
        return Proxy.newProxyInstance(
                mapperClass.getClassLoader(),
                new Class<?>[]{mapperClass},
                handler
        );
    }

    private Object newLab(int id, String labName, String roomNo) throws Exception {
        Class<?> labClass = Class.forName("org.example.backend.entity.LabEntity");
        Object lab = labClass.getConstructor().newInstance();
        invokeSetter(lab, "setId", Integer.class, id);
        invokeSetter(lab, "setLabName", String.class, labName);
        invokeSetter(lab, "setRoomNo", String.class, roomNo);
        return lab;
    }

    private Object newDevice() throws Exception {
        Class<?> deviceClass = Class.forName("org.example.backend.entity.DevicesEntity");
        return deviceClass.getConstructor().newInstance();
    }

    private void invokeNormalizeDevice(Object service, Object device) throws Exception {
        Method method = service.getClass().getDeclaredMethod("normalizeDevice", device.getClass());
        method.setAccessible(true);
        method.invoke(service, device);
    }

    private void invokeSetter(Object target, String methodName, Class<?> parameterType, Object value) throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterType);
        method.invoke(target, value);
    }

    private Object invokeObject(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception error) {
            throw new AssertionError("Failed to invoke " + methodName, error);
        }
    }

    private String invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception error) {
            throw new AssertionError("Failed to invoke " + methodName, error);
        }
    }
}
