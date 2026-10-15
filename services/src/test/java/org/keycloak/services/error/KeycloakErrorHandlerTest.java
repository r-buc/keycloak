package org.keycloak.services.error;

import javax.naming.directory.InvalidSearchFilterException;

import java.lang.reflect.Proxy;
import java.util.List;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;
import org.keycloak.services.DefaultKeycloakContext;
import org.keycloak.services.DefaultKeycloakSession;
import org.keycloak.services.DefaultKeycloakSessionFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KeycloakErrorHandlerTest {

    @Test
    void ldapSerializationFailureReturnsSanitizedServerError() {
        ModelException ldapFailure = new ModelException("Failed to fetch results from the LDAP [internal-provider]",
                new ModelException("Querying of LDAP failed", new InvalidSearchFilterException("Missing 'equals'")));
        JsonMappingException serializationFailure = JsonMappingException.from((JsonParser) null,
                ldapFailure.getMessage(), ldapFailure);

        assertServerError(serializationFailure, "internal-provider", "Missing 'equals'");
    }

    @Test
    void duplicateMapperSerializationFailureReturnsSanitizedServerError() {
        JsonMappingException serializationFailure = JsonMappingException.from((JsonParser) null,
                "Duplicate key modifyTimestamp", new IllegalStateException("internal attribute metadata"));

        assertServerError(serializationFailure, "modifyTimestamp", "internal attribute metadata");
    }

    @Test
    void malformedJsonRemainsBadRequest() {
        try (KeycloakSession session = createSession()) {
            Response response = KeycloakErrorHandler.getResponse(session,
                    new JsonParseException((JsonParser) null, "Unexpected token"));
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals("Cannot parse the JSON",
                    ((OAuth2ErrorRepresentation) response.getEntity()).getErrorDescription());
        }
    }

    private static void assertServerError(Throwable failure, String... internalDetails) {
        try (KeycloakSession session = createSession()) {
            Response response = KeycloakErrorHandler.getResponse(session, failure);
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            String description = ((OAuth2ErrorRepresentation) response.getEntity()).getErrorDescription();
            assertEquals("For more on this error consult the server log.", description);
            for (String internalDetail : internalDetails) {
                assertFalse(description.contains(internalDetail));
            }
        }
    }

    private static KeycloakSession createSession() {
        DefaultKeycloakSessionFactory factory = new DefaultKeycloakSessionFactory() {
            @Override
            public KeycloakSession create() {
                return new DefaultKeycloakSession(this) {
                    @Override
                    protected DefaultKeycloakContext createKeycloakContext(KeycloakSession session) {
                        return new DefaultKeycloakContext(session) {
                            @Override
                            public HttpHeaders getRequestHeaders() {
                                return (HttpHeaders) Proxy.newProxyInstance(HttpHeaders.class.getClassLoader(),
                                        new Class<?>[] {HttpHeaders.class}, (proxy, method, args) ->
                                                method.getName().equals("getAcceptableMediaTypes")
                                                        ? List.of(MediaType.APPLICATION_JSON_TYPE) : null);
                            }
                        };
                    }
                };
            }
        };
        return factory.create();
    }
}
