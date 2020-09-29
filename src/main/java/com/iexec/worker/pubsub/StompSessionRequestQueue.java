// /*
//  * Copyright 2020 IEXEC BLOCKCHAIN TECH
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package com.iexec.worker.pubsub;

// import java.util.Optional;
// import java.util.concurrent.BlockingQueue;
// import java.util.concurrent.LinkedBlockingQueue;

// import org.springframework.retry.annotation.Recover;
// import org.springframework.retry.annotation.Retryable;
// import org.springframework.stereotype.Component;

// import lombok.extern.slf4j.Slf4j;

// // TODO move this to common.
// @Slf4j
// @Component
// public class StompSessionRequestQueue {

//     private final BlockingQueue<StompSessionRequest> queue = new LinkedBlockingQueue<>();

//     public StompSessionRequestQueue() {
//         // this.queue = 
//     }

//     /**
//      * Add element to the queue and retry up to 3 times
//      * if a problem occurs.
//      * @param sessionRequest the element to be added.
//      * @return true if the element was added successfully,
//      * false if not.
//      */
//     @Retryable
//     public boolean add(StompSessionRequest sessionRequest) {
//         boolean isAdded = queue.add(sessionRequest);
//         if (isAdded) {
//             log.debug("Added STOMP session request to queue ");
//         }
//         return isAdded;
//     }

//     @Recover
//     private boolean add(Exception e, StompSessionRequest stompSessionRequest) {
//         log.error("Cannot add STOMP session request to queue");
//         e.printStackTrace();
//         return false;
//     }

//     /**
//      * Get the first inserted element in the queue or
//      * wait if the queue is empty
//      * @return {@link StompSessionRequest}
//      */
//     public Optional<StompSessionRequest> getOrWaitUntilAvailable() {
//         // queue.take() method waits for an element
//         // to be available if the queue is empty
//         try {
//             return Optional.of(queue.take());
//         } catch (InterruptedException e) {
//             log.error("Interrupted while waiting for tasks in the queue");
//             e.printStackTrace();
//             return Optional.empty();
//         }
//     }

//     public boolean isEmpty() {
//         return queue.isEmpty();
//     }
// }