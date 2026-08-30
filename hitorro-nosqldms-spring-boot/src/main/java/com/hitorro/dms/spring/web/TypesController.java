/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.TypeDef;
import com.hitorro.dms.service.TypeRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only surface for the registered document types. The UI hits
 *  this to render the type picker + dynamic form. */
@RestController
@RequestMapping("/api/types")
public class TypesController {

    private final TypeRegistry types;

    public TypesController(TypeRegistry types) { this.types = types; }

    @GetMapping
    public List<TypeDef> list() { return types.all(); }

    @GetMapping("/{name}")
    public ResponseEntity<TypeDef> get(@PathVariable("name") String name) {
        return types.get(name).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
