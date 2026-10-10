package com.membora.platform.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/custom-fields")
public class CustomFieldController {
    private static final Set<String> TYPES=Set.of("TEXT","NUMBER","DATE","BOOLEAN","DROPDOWN","MULTI_SELECT","FILE","PHONE","EMAIL");
    private final CustomFieldRepository fields; private final MemberCustomValueRepository values; private final MemberRepository members;
    public CustomFieldController(CustomFieldRepository f,MemberCustomValueRepository v,MemberRepository m){fields=f;values=v;members=m;}
    @GetMapping @PreAuthorize("hasAuthority('MEMBER_READ')")
    List<CustomField> list(@RequestHeader("X-Organization-Id") UUID org,@RequestParam(defaultValue="MEMBER") String entityType){return fields.findByOrganizationIdAndEntityTypeAndActiveTrueOrderByDisplayOrder(org,entityType);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ORG_MANAGE')")
    CustomField create(@RequestHeader("X-Organization-Id") UUID org,@Valid @RequestBody CreateField r){
        var type=r.fieldType().toUpperCase(); if(!TYPES.contains(type))throw new IllegalArgumentException("Unsupported field type");
        return fields.save(new CustomField(org,r.entityType(),r.name(),r.fieldKey(),type,r.required(),r.validationRules(),r.displayOrder(),r.optionsJson(),r.sensitive()));
    }
    @PutMapping("/{fieldId}/members/{memberId}") @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    MemberCustomValue set(@RequestHeader("X-Organization-Id") UUID org,@PathVariable UUID fieldId,@PathVariable UUID memberId,@RequestBody FieldValue r){
        members.findByIdAndOrganizationId(memberId,org).orElseThrow(()->new IllegalArgumentException("Member not found"));
        var field=fields.findByIdAndOrganizationId(fieldId,org).orElseThrow(()->new IllegalArgumentException("Field not found")); validate(field,r.value());
        var value=values.findByOrganizationIdAndMemberIdAndCustomFieldId(org,memberId,fieldId).orElse(new MemberCustomValue(org,memberId,fieldId,r.value())); value.valueJson=r.value(); value.updatedAt=java.time.Instant.now(); return values.save(value);
    }
    private void validate(CustomField f,String value){ if(f.required&&(value==null||value.isBlank()))throw new IllegalArgumentException(f.name+" is required"); if(value==null||value.isBlank())return;
        try{switch(f.fieldType){case"NUMBER"->Double.parseDouble(value);case"DATE"->LocalDate.parse(value);case"BOOLEAN"->{if(!value.equals("true")&&!value.equals("false"))throw new Exception();}case"EMAIL"->{if(!value.matches("^[^@]+@[^@]+\\.[^@]+$"))throw new Exception();}default->{}}}catch(Exception e){throw new IllegalArgumentException("Invalid value for "+f.name);}}
    record CreateField(@NotBlank String entityType,@NotBlank String name,@Pattern(regexp="[a-z][a-z0-9_]*") String fieldKey,@NotBlank String fieldType,boolean required,String validationRules,int displayOrder,String optionsJson,boolean sensitive){}
    record FieldValue(String value){}
}
