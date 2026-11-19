package com.nexusai.platform.web;

import java.util.UUID;

/**
 * A resource does not exist <em>in the caller's scope</em>. A resource that exists in another
 * workspace is reported identically, so responses never reveal it.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " " + id + " not found");
    }
}
