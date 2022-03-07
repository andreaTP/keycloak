package org.keycloak.operator;

public interface StatusUpdater<T> {

    boolean updateStatus(T status);
}
