package com.st6.wc.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the shape of the {@code common/} base-entity scaffolding (Appendix C.2): {@code
 * AbstractAuditingEntity} as a {@code @MappedSuperclass} with the four audit fields, and {@code
 * PersistableUuidEntity} as a UUID-PK + {@code @Version} base. Belt-and-suspenders with the {@code
 * forbidLombokData} gate: no {@code @Data} on the superclasses.
 */
class BaseEntityShapeTest {

  @Test
  void abstractAuditingEntity_isMappedSuperclass_withFourAuditFields() {
    assertTrue(AbstractAuditingEntity.class.isAnnotationPresent(MappedSuperclass.class));
    assertFieldType(AbstractAuditingEntity.class, "createdBy", String.class);
    assertFieldType(AbstractAuditingEntity.class, "createdAt", Instant.class);
    assertFieldType(AbstractAuditingEntity.class, "updatedBy", String.class);
    assertFieldType(AbstractAuditingEntity.class, "updatedAt", Instant.class);
  }

  @Test
  void persistableUuidEntity_isMappedSuperclass_withUuidIdAndVersion() {
    assertTrue(PersistableUuidEntity.class.isAnnotationPresent(MappedSuperclass.class));
    Field id = field(PersistableUuidEntity.class, "id");
    assertEquals(UUID.class, id.getType());
    assertTrue(id.isAnnotationPresent(Id.class), "id must be the JPA @Id");
    Field version = field(PersistableUuidEntity.class, "version");
    assertTrue(version.isAnnotationPresent(Version.class), "version must carry @Version");
  }

  @Test
  void baseEntities_doNotUseLombokData() {
    for (Class<?> c : new Class<?>[] {AbstractAuditingEntity.class, PersistableUuidEntity.class}) {
      assertFalse(
          Arrays.stream(c.getAnnotations())
              .anyMatch(a -> a.annotationType().getName().equals("lombok.Data")),
          c.getSimpleName() + " must not use Lombok @Data");
    }
  }

  @Test
  void concreteSubclass_inheritsIdVersionAndAuditAccessors() {
    // Exercises the @MappedSuperclass constructor chain + the Lombok @Getter/@Setter accessors.
    SampleEntity e = new SampleEntity();
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-06-01T00:00:00Z");
    e.setId(id);
    e.setVersion(0L);
    e.setCreatedBy("system");
    e.setCreatedAt(now);
    e.setUpdatedBy("system");
    e.setUpdatedAt(now);
    assertEquals(id, e.getId());
    assertEquals(0L, e.getVersion());
    assertEquals("system", e.getCreatedBy());
    assertEquals(now, e.getCreatedAt());
    assertEquals("system", e.getUpdatedBy());
    assertEquals(now, e.getUpdatedAt());
  }

  /** Minimal concrete subclass used only to exercise the abstract base chain in tests. */
  static final class SampleEntity extends PersistableUuidEntity {}

  private static Field field(Class<?> type, String name) {
    try {
      return type.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new AssertionError(type.getSimpleName() + " is missing field '" + name + "'", e);
    }
  }

  private static void assertFieldType(Class<?> type, String name, Class<?> expected) {
    assertEquals(expected, field(type, name).getType(), name + " has unexpected type");
  }
}
