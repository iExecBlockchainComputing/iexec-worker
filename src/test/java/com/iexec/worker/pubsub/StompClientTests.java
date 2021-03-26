package com.iexec.worker.pubsub;

import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class StompClientTests {

    @InjectMocks
    private StompClient stompClient;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }
}