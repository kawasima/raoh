package net.unit8.raoh.gsh.testdomain;

/**
 * Test class representing a domain entity with multiple constructors.
 */
public class TestUser {
    private final String name;
    private final int age;

    /**
     * Primary constructor.
     *
     * @param name the user name
     * @param age  the user age
     */
    public TestUser(String name, int age) {
        this.name = name;
        this.age = age;
    }

    /**
     * Delegating constructor with default age.
     *
     * @param name the user name
     */
    public TestUser(String name) {
        this(name, 0);
    }

    /**
     * Returns the user name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the user age.
     *
     * @return the age
     */
    public int getAge() {
        return age;
    }
}
