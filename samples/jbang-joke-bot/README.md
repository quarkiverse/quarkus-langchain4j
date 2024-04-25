# JBang sample aka getting an AI-powered bot in 13 lines of Java

To run the sample, you need JBang installed. If you don't have it, choose
one of the installation options from the [JBang
website](https://www.jbang.dev/download/).

You also have to set your OpenAI API key:

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, run the example with:

```
jbang jokes.java
```

To have it tell you a joke, call `http://localhost:8080/joke` with a GET
request.

Explanation: The code contains a single method which injects a
`io.vertx.ext.web.Router`, which is a class from the `quarkus-vertx-http`
extension responsible for routing requests to appropriate handlers. The
method is called during application boot thanks to the `@Observes`
annotation, and it uses the injected `Router` to define a single route on
the `/joke` path. The route's handler (the lambda expression that accepts a `rc` -
`RoutingContext` parameter) invokes the model and sets the HTTP response.
See [Quarkus documentation](https://quarkus.io/guides/reactive-routes#using-the-vert-x-web-router)
for more information.