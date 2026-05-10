package com.personalblog.ragbackend.mcp.endpoint;

import com.personalblog.ragbackend.mcp.protocol.JsonRpcRequest;
import com.personalblog.ragbackend.mcp.protocol.JsonRpcResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpEndpoint {

    private final McpDispatcher dispatcher;

    public McpEndpoint(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/mcp")
    public ResponseEntity<?> handle(@RequestBody JsonRpcRequest request) {
        JsonRpcResponse response = dispatcher.dispatch(request);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }
}
