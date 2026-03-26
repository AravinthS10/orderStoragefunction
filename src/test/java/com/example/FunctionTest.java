package com.example;

import com.microsoft.azure.functions.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Unit test for Function class.
 */
public class FunctionTest {
    /**
     * Unit test for HttpTriggerJava method.
     */

    @Test
    public void testHttpTriggerJava() throws Exception {
        // Mock request
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);
        String sessionId = "test-session";
        String requestBody = "{\"sessionId\":\"" + sessionId + "\"}";
        when(req.getBody()).thenReturn(Optional.of(requestBody));
        when(req.createResponseBuilder(any(HttpStatus.class)))
                .thenAnswer(invocation -> new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(invocation.getArgument(0)));

        // Mock blob output binding
        @SuppressWarnings("unchecked")
        final OutputBinding<String> outputBlob = mock(OutputBinding.class);

        // Mock context
        final ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());

        // Call function
        Function function = new Function();
        HttpResponseMessage response = function.run(req, outputBlob, context);

        // Verify
        assertEquals(HttpStatus.OK, response.getStatus());
        verify(outputBlob, times(1)).setValue(contains(sessionId));
    }
}
