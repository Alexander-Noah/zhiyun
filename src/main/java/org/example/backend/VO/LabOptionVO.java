package org.example.backend.VO;

public class LabOptionVO {
    private Long id;
    private String labName;

    public LabOptionVO() {
    }

    public LabOptionVO(Long id, String labName) {
        this.id = id;
        this.labName = labName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }
}
