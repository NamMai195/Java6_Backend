package com.backend.controller.response;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public  abstract class PageResponeAbstract implements Serializable {
    public int pageNumber;
    public int pageSize;
    public int totalPages;
    public int totalElements;
}
