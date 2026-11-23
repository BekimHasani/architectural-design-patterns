

1. In the PaymentApiTest a lost response charges the customer twice 
    - the issue is identified, the system must separate the Nothing-charged from other provider response errors 
2. Two merchants are using the same idempotent key
    - The solution: The only way i can think of is returning the Payment object from paymentService charge method, 
    - in this way we can check which merchant initiation that payment and compare with the merchant id that is provided 
    - as a parameter in the method. Doing so we break unit tests. Breaking unit tests breaks the challenge rules. 
3. Refunds together are exceeding the capture amount
   - the refunded amount was not included in the total amount, so the amount remained the same even after a refund was completed
4. A lost response is never retried to by the customer to another provider
   - for fixing this issue we need a reconciliation functionality  
5. The same key on another operation is a different request
   - no fix for the same reason as question task 2
6. The same key from another merchant is a different request
   - no fix for the same reason as question task 2