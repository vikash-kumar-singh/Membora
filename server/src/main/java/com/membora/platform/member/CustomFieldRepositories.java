package com.membora.platform.member;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface CustomFieldRepository extends JpaRepository<CustomField,UUID> { List<CustomField> findByOrganizationIdAndEntityTypeAndActiveTrueOrderByDisplayOrder(UUID org,String entity); Optional<CustomField> findByIdAndOrganizationId(UUID id,UUID org); }
interface MemberCustomValueRepository extends JpaRepository<MemberCustomValue,UUID> { Optional<MemberCustomValue> findByOrganizationIdAndMemberIdAndCustomFieldId(UUID org,UUID member,UUID field); List<MemberCustomValue> findByOrganizationIdAndMemberId(UUID org,UUID member); }
