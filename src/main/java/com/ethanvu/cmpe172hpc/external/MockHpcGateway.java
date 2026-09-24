package com.ethanvu.cmpe172hpc.external;

import org.springframework.stereotype.Component;

@Component
public class MockHpcGateway implements HpcGateway {
    @Override
    public String getClusterStatus() {
        return "MOCK_AVAILABLE";
    }
}
