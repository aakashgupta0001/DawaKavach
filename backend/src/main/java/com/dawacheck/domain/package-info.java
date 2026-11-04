/**
 * The fourteen tables of section 12, as JPA entities.
 *
 * These classes use public fields rather than getter/setter pairs. Hibernate
 * reads fields directly, the DTO layer in {@code com.dawacheck.dto} is what the
 * API actually returns, and nothing outside the service layer touches an
 * entity, so the accessors would be noise.
 *
 * That choice has one consequence worth stating, because getting it wrong is
 * silent: a {@code @ManyToOne} that the code reads through must not be
 * {@code LAZY}. A lazy association hands back a generated proxy subclass whose
 * own fields are never populated, and the interception that loads the row sits
 * in the getters. Reading {@code scan.member.id} on a lazy proxy therefore
 * yields null rather than the id, with no exception and no log line. So every
 * association whose contents are read here is {@code EAGER}, and the only ones
 * left {@code LAZY} are back-references that nothing reads.
 */
package com.dawacheck.domain;
