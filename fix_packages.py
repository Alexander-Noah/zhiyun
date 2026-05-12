import os
for root, dirs, files in os.walk('src/main/java/org/example/backend'):
    for file in files:
        if file.endswith('.java'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            new_content = content.replace('package org.example.backend.service.Impl;', 'package org.example.backend.service.impl;')
            new_content = new_content.replace('package org.example.backend.Result;', 'package org.example.backend.result;')
            new_content = new_content.replace('import org.example.backend.Result.Result;', 'import org.example.backend.result.Result;')
            if content != new_content:
                with open(path, 'w', encoding='utf-8', newline='') as f:
                    f.write(new_content)
