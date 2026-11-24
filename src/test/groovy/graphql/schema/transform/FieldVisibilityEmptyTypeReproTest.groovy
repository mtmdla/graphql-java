package graphql.schema.transform

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.GraphQLDirectiveContainer
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Repro: a field, argument or input field that stays visible references a type whose fields are all hidden.
 * <p>
 * The transformation deletes the emptied type but not the elements that reference it, and fails with an internal
 * {@link AssertException}. The tests only require that no internal assertion escapes, so they pass whether the
 * outcome is a cascading removal or a clear {@link graphql.schema.validation.InvalidSchemaException}.
 * They currently FAIL.
 */
class FieldVisibilityEmptyTypeReproTest extends Specification {

    static final String HEAD = "directive @private on FIELD_DEFINITION | INPUT_FIELD_DEFINITION\n"

    def transformation = new FieldVisibilitySchemaTransformation({ environment ->
        def directives = (environment.schemaElement as GraphQLDirectiveContainer).appliedDirectives
        return directives.find({ it.name == "private" }) == null
    })

    private Throwable applyAndCapture(String sdl) {
        try {
            transformation.apply(TestUtil.schema(sdl))
            return null
        } catch (Throwable t) {
            return t
        }
    }

    @Unroll
    def "#name does not fail with an internal assertion"() {
        when:
        def thrown = applyAndCapture(HEAD + sdl)

        then:
        !(thrown instanceof AssertException)

        where:
        name                                | sdl
        "field of emptied type"             | "type Query { keep: String ref: Empty } type Empty { secret: String @private }"
        "non-null field of emptied type"    | "type Query { keep: String ref: Empty! } type Empty { secret: String @private }"
        "list field of emptied type"        | "type Query { keep: String ref: [Empty] } type Empty { secret: String @private }"
        "non-null list of emptied type"     | "type Query { keep: String ref: [Empty!]! } type Empty { secret: String @private }"
        "argument of emptied input type"    | "type Query { keep: String f(a: EmptyIn): String } input EmptyIn { secret: String @private }"
        "non-null argument, emptied input"  | "type Query { keep: String f(a: EmptyIn!): String } input EmptyIn { secret: String @private }"
        "input field of emptied input type" | "type Query { keep: String f(a: Outer): String } input Outer { keep: String ref: EmptyIn } input EmptyIn { secret: String @private }"
        "non-null input field, emptied"     | "type Query { keep: String f(a: Outer): String } input Outer { keep: String ref: EmptyIn! } input EmptyIn { secret: String @private }"
        "field of emptied interface"        | "type Query { keep: String i: I } interface I { s: String @private } type A implements I { s: String @private }"
        "union with all members emptied"    | "type Query { keep: String u: U } union U = A | B type A { s: String @private } type B { s: String @private }"
    }

    @Unroll
    def "control: #name works today"() {
        when:
        def thrown = applyAndCapture(HEAD + sdl)

        then:
        thrown == null

        where:
        name                                  | sdl
        "referencing field is also hidden"    | "type Query { keep: String ref: Empty @private } type Empty { secret: String @private }"
        "union with only some members emptied"| "type Query { keep: String u: U } union U = A | B type A { s: String @private } type B { s: String }"
    }
}
