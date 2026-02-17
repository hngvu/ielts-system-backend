package io.gsp26se16.moni.payment.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PackagePricingResponse {
    Integer id;
    String name;
    Integer price;
    Integer creditAmount;
    Boolean isActive;
}
