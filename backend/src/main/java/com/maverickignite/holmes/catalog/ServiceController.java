package com.maverickignite.holmes.catalog;

import com.maverickignite.holmes.catalog.ServiceDtos.ServiceRequest;
import com.maverickignite.holmes.catalog.ServiceDtos.ServiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

  private final ServiceCatalog catalog;

  @GetMapping
  List<ServiceResponse> list() {
    return catalog.findAll().stream().map(ServiceResponse::from).toList();
  }

  @GetMapping("/{id}")
  ServiceResponse get(@PathVariable Long id) {
    return ServiceResponse.from(catalog.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
    return ServiceResponse.from(catalog.create(request));
  }

  @PutMapping("/{id}")
  ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
    return ServiceResponse.from(catalog.update(id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable Long id) {
    catalog.remove(id);
  }
}
