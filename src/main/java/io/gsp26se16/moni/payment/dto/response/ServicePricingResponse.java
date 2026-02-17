package io.gsp26se16.moni.payment.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServicePricingResponse {
    Integer id;
    String serviceCode;
    String name;
    String description;
    Integer creditCost;
}
