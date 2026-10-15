/** Catálogo de serviços e ordens de serviço. Único módulo de negócio que usa outros. */
@ApplicationModule(displayName = "Serviços", allowedDependencies = {"shared", "customer", "product"})
package com.luthier.service;

import org.springframework.modulith.ApplicationModule;
