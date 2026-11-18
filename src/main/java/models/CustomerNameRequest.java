package models;

import generators.GeneratingRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerNameRequest extends BaseModel {
    @GeneratingRule(regex = "[A-Z][a-z]{2,9} [A-Z][a-z]{2,9}")
    private String name;
}
