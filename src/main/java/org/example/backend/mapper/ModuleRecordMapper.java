package org.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModuleRecordMapper {
    List<String> listRecordJson(@Param("moduleName") String moduleName);

    void deleteByModuleName(@Param("moduleName") String moduleName);

    void insertRecord(
            @Param("moduleName") String moduleName,
            @Param("recordKey") String recordKey,
            @Param("recordJson") String recordJson
    );
}
