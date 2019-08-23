package com.iexec.worker.feign;

import java.util.function.Function;

import org.springframework.http.ResponseEntity;

/**
 * This is a generic interface to define an HTTP call.
 * T: type of the response body. It can be Void.
 * Object[]: array of the call arguments
 */

public interface HttpCall<T> extends Function<Object[], ResponseEntity<T>> {}