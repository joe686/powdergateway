package com.powergateway.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FormulaValidateResult {
    private boolean ok = true;
    private List<ErrorItem> errors = new ArrayList<>();

    public void addError(String path, String message) {
        this.ok = false;
        ErrorItem e = new ErrorItem();
        e.setPath(path);
        e.setMessage(message);
        this.errors.add(e);
    }

    @Data
    public static class ErrorItem {
        /** JSON path，例如 children[0].right */
        private String path;
        private String message;
    }
}
