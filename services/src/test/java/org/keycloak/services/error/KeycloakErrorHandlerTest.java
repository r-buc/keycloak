package org.keycloak.services.error;

import javax.naming.directory.InvalidSearchFilterException;

import jakarta.ws.rs.core.Response;

import org.keycloak.models.ModelException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KeycloakErrorHandlerTest {

    @Test
    void responseSerializationFailureReturnsSanitizedServerError() {
        ModelException ldapFailure = new ModelException("Failed to fetch results from the LDAP [internal-provider]",
                new ModelException("Querying of LDAP failed", new InvalidSearchFilterException("Missing 'equals'")));
        JsonMappingException serializationFailure = JsonMappingException.from((JsonParser) null,
                ldapFailure.getMessage(), ldapFailure);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR, KeycloakErrorHandler.getResponseStatus(serializationFailure));

        String description = KeycloakErrorHandler.getErrorDescription(serializationFailure);
        assertEquals("For more on this error consult the server log.", description);
        assertFalse(description.contains("internal-provider"));
        assertFalse(description.contains("Missing 'equals'"));
    }

    @Test
    void malformedRequestJsonRemainsBadRequest() {
        JsonParseException malformedJson = new JsonParseException((JsonParser) null, "Unexpected token");

        assertEquals(Response.Status.BAD_REQUEST, KeycloakErrorHandler.getResponseStatus(malformedJson));
        assertEquals("Cannot parse the JSON", KeycloakErrorHandler.getErrorDescription(malformedJson));
    }
}
