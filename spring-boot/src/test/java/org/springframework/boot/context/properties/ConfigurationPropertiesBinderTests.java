/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationErrors;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * Tests for {@link ConfigurationPropertiesBinder}.
 *
 * @author Stephane Nicoll
 */
public class ConfigurationPropertiesBinderTests {

	private final MockEnvironment environment = new MockEnvironment();

	@Test
	public void bindSimpleProperties() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"person.name=John Smith", "person.age=42");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PersonProperties target = new PersonProperties();
		binder.bind(target);
		assertThat(target.name).isEqualTo("John Smith");
		assertThat(target.age).isEqualTo(42);
	}

	@Test
	public void bindUnknownFieldFailureMessageContainsDetailsOfPropertyOrigin() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"person.does-not-exist=yolo");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PersonProperties target = new PersonProperties();
		try {
			binder.bind(target);
			fail("Expected exception");
		}
		catch (ConfigurationPropertiesBindingException ex) {
			BindException bindException = (BindException) ex.getCause();
			assertThat(bindException.getMessage())
					.startsWith("Failed to bind properties under 'person' to "
							+ PersonProperties.class.getName());
		}
	}

	@Test
	public void bindWithIgnoreInvalidFieldsAnnotation() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"com.example.bar=spam");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithIgnoreInvalidFields target = new PropertyWithIgnoreInvalidFields();
		binder.bind(target);
		assertThat(target.getBar()).isEqualTo(0);
	}

	@Test
	public void bindNonAnnotatedObject() {
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		binder.bind("FooBar");
	}

	@Test
	public void bindToEnum() {
		bindToEnum("test.theValue=foo");
	}

	@Test
	public void bindToEnumRelaxed() {
		bindToEnum("test.the-value=FoO");
		bindToEnum("test.THE_VALUE=FoO");
	}

	private void bindToEnum(String property) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				property);
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithEnum target = new PropertyWithEnum();
		binder.bind(target);
		assertThat(target.getTheValue()).isEqualTo(FooEnum.FOO);
	}

	@Test
	public void bindSetOfEnumRelaxed() {
		bindToEnumSet("test.the-values=foo,bar", FooEnum.FOO, FooEnum.BAR);
		bindToEnumSet("test.the-values=foo", FooEnum.FOO);
	}

	private void bindToEnumSet(String property, FooEnum... expected) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				property);
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithEnum target = new PropertyWithEnum();
		binder.bind(target);
		assertThat(target.getTheValues()).contains(expected);
	}

	@Test
	public void bindToCharArray() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.chars=word");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithCharArray target = new PropertyWithCharArray();
		binder.bind(target);
		assertThat(target.getChars()).isEqualTo("word".toCharArray());
	}

	@Test
	public void bindToRelaxedPropertyNamesSame() throws Exception {
		testRelaxedPropertyNames("test.FOO_BAR=test1", "test.FOO_BAR=test2",
				"test.BAR-B-A-Z=testa", "test.BAR-B-A-Z=testb");
	}

	private void testRelaxedPropertyNames(String... pairs) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				pairs);
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithRelaxedNames target = new PropertyWithRelaxedNames();
		binder.bind(target);
		assertThat(target.getFooBar()).isEqualTo("test2");
		assertThat(target.getBarBAZ()).isEqualTo("testb");
	}

	@Test
	public void bindToNestedProperty() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.nested.value=test1");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithNestedValue target = new PropertyWithNestedValue();
		binder.bind(target);
		assertThat(target.getNested().getValue()).isEqualTo("test1");
	}

	@Test
	public void bindToMap() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.map.foo=bar");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertiesWithMap target = new PropertiesWithMap();
		binder.bind(target);
		assertThat(target.getMap()).containsOnly(entry("foo", "bar"));
	}

	@Test
	public void bindToMapWithSystemProperties() {
		MutablePropertySources propertySources = new MutablePropertySources();
		propertySources.addLast(new SystemEnvironmentPropertySource("system",
				Collections.singletonMap("TEST_MAP_FOO_BAR", "baz")));
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				propertySources, null, null);
		PropertiesWithComplexMap target = new PropertiesWithComplexMap();
		binder.bind(target);
		assertThat(target.getMap()).containsOnlyKeys("foo");
		assertThat(target.getMap().get("foo")).containsOnly(entry("bar", "baz"));
	}

	@Test
	public void bindWithOverriddenProperties() {
		MutablePropertySources propertySources = new MutablePropertySources();
		propertySources.addFirst(new SystemEnvironmentPropertySource("system",
				Collections.singletonMap("PERSON_NAME", "Jane")));
		propertySources.addLast(new MapPropertySource("test",
				Collections.singletonMap("person.name", "John")));
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				propertySources, null, null);
		PersonProperties target = new PersonProperties();
		binder.bind(target);
		assertThat(target.name).isEqualTo("Jane");
	}

	@Test
	public void validationWithSetter() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.foo=spam");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithValidatingSetter target = new PropertyWithValidatingSetter();
		try {
			binder.bind(target);
			fail("Expected exception");
		}
		catch (ConfigurationPropertiesBindingException ex) {
			BindException bindException = (BindException) ex.getCause();
			assertThat(bindException.getMessage())
					.startsWith("Failed to bind properties under 'test' to "
							+ PropertyWithValidatingSetter.class.getName());
		}
	}

	@Test
	public void validationWithCustomValidator() {
		CustomPropertyValidator validator = spy(new CustomPropertyValidator());
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, validator);
		PropertyWithCustomValidator target = new PropertyWithCustomValidator();
		assertThat(bindWithValidationErrors(binder, target).getAllErrors()).hasSize(1);
		verify(validator).validate(eq(target), any(Errors.class));
	}

	@Test
	public void validationWithCustomValidatorNotSupported() {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.foo=bar");
		CustomPropertyValidator validator = spy(new CustomPropertyValidator());
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, validator);
		PropertyWithValidatingSetter target = new PropertyWithValidatingSetter();
		binder.bind(target);
		assertThat(target.getFoo()).isEqualTo("bar");
		verify(validator, times(0)).validate(eq(target), any(Errors.class));
	}

	private ValidationErrors bindWithValidationErrors(
			ConfigurationPropertiesBinder binder, Object target) {
		try {
			binder.bind(target);
			throw new AssertionError("Should have failed to bind " + target);
		}
		catch (ConfigurationPropertiesBindingException ex) {
			Throwable rootCause = ex.getRootCause();
			assertThat(rootCause).isInstanceOf(BindValidationException.class);
			return ((BindValidationException) rootCause).getValidationErrors();
		}
	}

	@Test
	public void propertyWithFallback() throws Exception {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.bar=fallbackValue");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithFallback target = new PropertyWithFallback();
		binder.bind(target);
		assertThat(target.getFoo()).isEqualTo("fallbackValue");
		assertThat(target.getBaz()).isEqualTo("fallbackValue");
	}

	@Test
	public void propertyWithFallbackFromDifferentNamespace() throws Exception {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"test.bar=fallbackValue");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithFallbackFromDifferentNamespace target = new PropertyWithFallbackFromDifferentNamespace();
		binder.bind(target);
		assertThat(target.getFoo()).isEqualTo("fallbackValue");
	}

	@Test
	public void propertyWithNonExistingFallback() throws Exception {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(this.environment,
				"foo=bar");
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		PropertyWithFallbackFromDifferentNamespace target = new PropertyWithFallbackFromDifferentNamespace();
		binder.bind(target);
		assertThat(target.getFoo()).isNull();
	}

	@Test
	public void invalidMethodAnnotated() throws Exception {
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		InvalidMethodAnnotated target = new InvalidMethodAnnotated();
		try {
			binder.bind(target);
			fail("Expected exception");
		}
		catch (ConfigurationPropertiesBindingException ex) {
			assertThat(ex.getRootCause()).isInstanceOf(ConfigurationPropertyValueBindingException.class);
		}
	}

	@Test
	public void fieldAndAccessorAnnotated() throws Exception {
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		FieldAndGetterAnnotated target = new FieldAndGetterAnnotated();
		try {
			binder.bind(target);
			fail("Expected exception");
		}
		catch (ConfigurationPropertiesBindingException ex) {
			assertThat(ex.getRootCause()).isInstanceOf(ConfigurationPropertyValueBindingException.class);
		}
	}

	@Test
	public void getterAndSetterAnnotated() throws Exception {
		ConfigurationPropertiesBinder binder = new ConfigurationPropertiesBinder(
				this.environment.getPropertySources(), null, null);
		GetterAndSetterAnnotated target = new GetterAndSetterAnnotated();
		try {
			binder.bind(target);
			fail("Expected exception");
		}
		catch (ConfigurationPropertiesBindingException ex) {
			assertThat(ex.getRootCause()).isInstanceOf(ConfigurationPropertyValueBindingException.class);
		}
	}

	@ConfigurationProperties(value = "person", ignoreUnknownFields = false)
	static class PersonProperties {

		private String name;

		private Integer age;

		public void setName(String name) {
			this.name = name;
		}

		public void setAge(Integer age) {
			this.age = age;
		}

	}

	@ConfigurationProperties(prefix = "com.example", ignoreInvalidFields = true)
	public static class PropertyWithIgnoreInvalidFields {

		private long bar;

		public void setBar(long bar) {
			this.bar = bar;
		}

		public long getBar() {
			return this.bar;
		}

	}

	@ConfigurationProperties(prefix = "test")
	public static class PropertyWithEnum {

		private FooEnum theValue;

		private List<FooEnum> theValues;

		public void setTheValue(FooEnum value) {
			this.theValue = value;
		}

		public FooEnum getTheValue() {
			return this.theValue;
		}

		public List<FooEnum> getTheValues() {
			return this.theValues;
		}

		public void setTheValues(List<FooEnum> theValues) {
			this.theValues = theValues;
		}

	}

	enum FooEnum {

		FOO, BAZ, BAR

	}

	@ConfigurationProperties(prefix = "test", ignoreUnknownFields = false)
	public static class PropertyWithCharArray {

		private char[] chars;

		public char[] getChars() {
			return this.chars;
		}

		public void setChars(char[] chars) {
			this.chars = chars;
		}

	}

	@ConfigurationProperties(prefix = "test")
	public static class PropertyWithRelaxedNames {

		private String fooBar;

		private String barBAZ;

		public String getFooBar() {
			return this.fooBar;
		}

		public void setFooBar(String fooBar) {
			this.fooBar = fooBar;
		}

		public String getBarBAZ() {
			return this.barBAZ;
		}

		public void setBarBAZ(String barBAZ) {
			this.barBAZ = barBAZ;
		}

	}

	@ConfigurationProperties(prefix = "test")
	public static class PropertyWithNestedValue {

		private Nested nested = new Nested();

		public Nested getNested() {
			return this.nested;
		}

		public static class Nested {

			private String value;

			public void setValue(String value) {
				this.value = value;
			}

			public String getValue() {
				return this.value;
			}

		}

	}

	@Validated
	@ConfigurationProperties(prefix = "test")
	public static class PropertiesWithMap {

		private Map<String, String> map;

		public Map<String, String> getMap() {
			return this.map;
		}

		public void setMap(Map<String, String> map) {
			this.map = map;
		}

	}

	@ConfigurationProperties(prefix = "test")
	public static class PropertiesWithComplexMap {

		private Map<String, Map<String, String>> map;

		public Map<String, Map<String, String>> getMap() {
			return this.map;
		}

		public void setMap(Map<String, Map<String, String>> map) {
			this.map = map;
		}

	}

	@ConfigurationProperties(prefix = "test")
	public static class PropertyWithValidatingSetter {

		private String foo;

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
			if (!foo.equals("bar")) {
				throw new IllegalArgumentException("Wrong value for foo");
			}
		}

	}

	@ConfigurationProperties(prefix = "custom")
	@Validated
	public static class PropertyWithCustomValidator {

		private String foo;

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

	}

	public static class CustomPropertyValidator implements Validator {

		@Override
		public boolean supports(Class<?> aClass) {
			return aClass == PropertyWithCustomValidator.class;
		}

		@Override
		public void validate(Object o, Errors errors) {
			ValidationUtils.rejectIfEmpty(errors, "foo", "TEST1");
		}

	}

	@Configuration
	@EnableConfigurationProperties
	@ConfigurationProperties(prefix = "test")
	public static class PropertyWithFallback {

		@ConfigurationPropertyValue(fallback = "test.bar")
		private String foo;

		private String bar;

		private String baz;

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

		public String getBar() {
			return this.bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}

		@ConfigurationPropertyValue(fallback = "test.bar")
		public String getBaz() {
			return this.baz;
		}

		public void setBaz(String baz) {
			this.baz = baz;
		}
	}

	@Configuration
	@EnableConfigurationProperties
	@ConfigurationProperties(prefix = "different-namespace")
	public static class PropertyWithFallbackFromDifferentNamespace {

		@ConfigurationPropertyValue(fallback = "test.bar")
		private String foo;

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String value) {
			this.foo = value;
		}
	}

	@Configuration
	@EnableConfigurationProperties
	@ConfigurationProperties
	public static class InvalidMethodAnnotated {

		@ConfigurationPropertyValue
		public void doStuff() {
		}
	}

	@Configuration
	@EnableConfigurationProperties
	@ConfigurationProperties
	public static class FieldAndGetterAnnotated {

		@ConfigurationPropertyValue
		private String value;

		@ConfigurationPropertyValue
		public String getValue() {
			return this.value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Configuration
	@EnableConfigurationProperties
	@ConfigurationProperties
	public static class GetterAndSetterAnnotated {

		private String value;

		@ConfigurationPropertyValue
		public String getValue() {
			return this.value;
		}

		@ConfigurationPropertyValue
		public void setValue(String value) {
			this.value = value;
		}
	}

}
