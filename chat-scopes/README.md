# Quarkus Chat Scopes

Quarkus Chat Scopes is an extension built on top of [Quarkus Langchain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html) to help you manage local and remote conversations
with your LLM.  It is a great framework for writing chat applications that have a web, mobile, or command-line chat UI.   Allowing you to easily marry application state with Chat Memory and LLM state.


```java
@ChatScoped
@RegisterAiService
public interface WeaponBuilder {

    @ChatRoute("weaponBuilder")
    @SystemMessage("Chat with user to build them a new weapon for their video game")
    String build(@UserMessage msg);
}
```

The `@ChatRoute` annotation makes the AiService `WeaponBuilder.build()` immediately invokable by a remote client.  `@ChatScoped` beans allow you to demarcate conversations so you can
easily evict Chat memory when the conversation is finished.  `@ChatScoped` beans work differently so you don't have to juggle memory ids with the `@MemoryId` annotation and can rely on default ids that last between chat requests.  The chat route API allows you to easily send back events asynchronously to the client so it can do things like render nice
UIs as part of your chats.  The chat scope also remembers the current route so the client doesn't have to, allowing the server to drive the conversation.

In a nutshell, here's a summary of what chat scopes provides:

* No need to juggle chat memory ids.  New default ID strategies introduced.
* A new CDI nestable conversational scope (`@ChatScoped` and `@PerChatScoped`).
* Another new CDI scope (`@InvocationScoped`).  A scope that starts and end with a bean method call.  Great for one-off AiService method calls.
* A new event-based chat invocation framework for executing on beans and AiServices (`@ChatRoute`).
* A local and remote client API (java and javascript) that can use to chat with enabled beans and AiServices.  Great for developing Chat UIs.
* APIs for tighter control over Chat Memory


# New CDI conversational scope: `@ChatScoped`

Quarkus Chat Scopes introduces new conversational scope that sits between `@SessionScoped` and `@RequestScoped`.   It is manually started and stopped.
Conversations can also be nested: pushed and popped.   We'll see later that with the `@ChatRoute` API this gives you a powerful way to have and manage conversations with your LLM.

```java
import io.quarkiverse.langchain4j.chatscopes.ChatScope;

{
    // start a new conversation
    ChatScope.begin();
    
    // start a nested conversation
    ChatScope.push();

    // end current nested conversation and revert to previous conversation
    ChatScope.pop();

    // end the conversation
    ChatScope.end();
    
}
```

The `ChatScope` API also introduces two new CDI bean scope annotations: `@ChatScoped` and `@PerChatScoped`.  


## `@ChatScoped`

For `@ChatScoped` beans, they are created when they are first
accessed and their lifecycle is tied to the current chat scope they were created in.  This bean instance is inherited within nested chat scopes.  
Here's an example:

```java
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;

@ChatScoped
public CounterBean {
    int counter;

    public int get() {
        return counter;
    }

    public void increment() {
        counter++;
    }
}

@ApplicationScoped
public MyBean {
    @Inject
    CounterBean counter;

    public void conversation_1() {
        try {
            // this fails because a chat scope context is not active.
            counter.get(); 

        } catch (ContextNotActiveException ignored) {

        }
        ChatScope.begin();
        // 1st bean access creates it and ties it to current scope
        counter.increment(); 
        counter.increment();
        
        // returns a value of 2
        counter.get();

        // start a nested conversation
        ChatScope.push();
        // counter from parent scope is inherited
        // This outputs 2
        counter.get();

        ChatScope.pop();
        // Outputs 2
        counter.get();

        ChatScope.end(); // destroys the counter bean
    }

    public void conversation_2() {
        ChatScope.begin();

        // start a nested conversation
        ChatScope.push();
        // counter from parent scope is inherited
        // This outputs 2
        counter.increment();
        counter.increment();

        // value is 2
        counter.get();

        ChatScope.pop(); // destroys nested counter bean
        
        // creates a new bean as it this is the first time it was accessed in top scope
        // Outputs 0, 
        counter.get();

        ChatScope.end(); // destroys the counter bean
    }
}
```

The `conversation_1()` method shows how `@ChatScoped` beans are inherited in nested scopes.  The `conversation_2()` method shows how
beans are destroyed when the current scope is destroyed.

## `@PerChatScoped`

For `@PerChatScoped` beans, they work the same as `@ChatScoped` except that they are not inherited by their parent scopes when having nested conversations.
A new `@PerChatScoped` bean is created per chat scope.  Nested or not.  Here's an example:

```java
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.PerChatScoped;

@PerChatScoped
public CounterBean {
    int counter;

    public int get() {
        return counter;
    }

    public void increment() {
        counter++;
    }
}

@ApplicationScoped
public MyBean {
    @Inject
    CounterBean counter;

    public void conversation_1() {

        ChatScope.begin();
        // 1st bean access creates it and ties it to current scope
        counter.increment();

        // returns a value of 1
        counter.get();
        
        // start a nested conversation
        ChatScope.push();
        // A new counter is created per chat scope
        // This outputs 0 as its a new bean
        counter.get();
        counter.increment();
        counter.increment();
        counter.increment();

        // outputs 3
        counter.get();

        // destroys the nested counter
        ChatScope.pop();
\
        // top scope has its own counter instance
        // Outputs 1
        counter.get();

        ChatScope.end(); // destroys the counter bean
    }
}
```

# Another new CDI scope:  `@InvocationScoped`

For `@InvocationScoped` beans, their lifecycle begins and ends with the top level method call on a `@InvocationScoped` bean.
This scope is great for on-off where you only want to hold state for the duration of a method call.  Think of it as a finer grain
scope than `@RequestScoped`.

Here's a simple example how they work:

```java
import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;

@InvocationScoped
public class CounterBean {

    int counter = 0;

    public void increment() {
        counter++;
    }

    public int getCounter() {
        return counter;
    }

}

@ApplicationScoped
public class MyBean {
    @Inject
    CounterBean counter;

    public void executeCounter() {
        counter.increment();

        // value will be zero as bean is created and destroyed per method call
        counter.getCounter();
    }
}
```

The invocation scope lasts for the duration of the top-level method call.  So, any other invocation scoped beans accessed
within that top-level method call will retain their state.  This could be useful with AiServices.  Make your AiService and `@Toolbox` classes
`@InvocationScoped` and this is a great way to hold
state and chat memory for only the duration of a AiService method call that has multiple tool calls.

Here's a simple example to illustrate:

```java
import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;

@InvocationScoped
public class CounterBean {

    int counter = 0;

    public void increment() {
        counter++;
    }

    public int getCounter() {
        return counter;
    }

}

@InvocationScoped
public class CalculateBean {

    @Inject
    CounterBean counterBean;

    public int add() {
        counterBean.increment();
        counterBean.increment();
        // value returned will be 2 because
        // the add() method is top-level invocation
        return counterBean.getCounter();
    }

}
```

# No need for `@MemoryId` anymore:  default ID support

With chat scopes, you don't need to use the `@MemoryId` annotation anymore.  For chat scoped beans, a new default memory ID is generated per chat scope.
This allows you to retain chat memory between requests so you don't have to juggle memory IDs yourself.  When the chat scope dies, the memory gets cleaned up with it.

`@InvocationScoped` beans work similarly.  A new default memory ID is generated that lasts for the duration of the top-level method invocation.

For `@ChatScoped` or `@PerChatScoped` AiServices, the default memory ID is generated as follows:
`<current chat scope id>#<FQN of Class>.<method-name>`.

For `@InvocationScoped` beans a new default memory ID is generated for the top method invocation.
`<id-per-top-level-method-call>#<FQN of Class>.<method-name>`

The old default memory ID strategy for other CDI scopes(application, session, and request) still remains. For them, the default ID derives from the
current CDI request context plus the FQN of the AiService method being invoked.  `<requestContextId>#<FQN of Class>.<method-name>`.

# Chat invocation framework: `@ChatRoute`

Chat scopes also comes with an chat invocation framework.  It provides a lot of structure for writing chat UI applications by allowing you to define chat endpoints and providing a local and remote client to synchronously and asynchorously invoke on those endpoints.  These endpoints are called chat routes and declared by applying the `@ChatRoute` annotation to any bean or AiService method.

```java
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService 
@ChatScoped
public interface RagBot {

    @ChatRoute("RagBot")
    @SystemMessage("""
            You are an AI named Bob answering questions about financial products.
            Your response must be polite, use the same language as the question, and be relevant to the question.

            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    String chat(@UserMessage String question);
}

```

Chat routes are also tied closely with chat scopes.  When a chat route is associated with a chat scope, each invocation sent by the client is routed to the current route associated with the
current chat scope.  This allows the server to drive the conversation and the client doesn't have to worry about managing the navigation between different endpoints.  It just sends
the user message to the chat route endpoint and receives events asynchrously as responses to render.


## Local client

Chat routes have a local client which you can use to invoke on a chat route-based service.  (There's a websocket and javascript websocket based client too, but we'll talk about that later).

Usage of the client consists of creating a `Client` object, using the `SessionBuilder` to register event handlers that process events sent from your chat routes, and then finally connecting to the chat route service
to create a `Session` where you can make chat requests.  Closing the `Client` object deletes all sessions and any chat scoped beans those sessions still have active.  

Let's walk through an example.  Here's the chat route we are going
to create a client for:

```java
@RegisterAiService 
@ChatScoped
public interface RagBot {

    @ChatRoute("RagBot")
    @SystemMessage("""
            You are an AI named Bob answering questions about financial products.
            Your response must be polite, use the same language as the question, and be relevant to the question.

            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    String chat(@UserMessage String question);
}
```

Return values from AiService methods automatically get turned into events that are sent back to the client.  This will be explained in more detail later, but a `String` return value
automatically gets converted into a `Message` event and the string is sent back within that event.  There's mappings for other return types, but we'll discuss that in another
chapter.

To continue our example, let's write a command line chat UI which consumes our RAG Ai Service using the local API:

```java
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import java.util.Scanner;

@QuarkusMain
public class ChatApp implements QuarkusApplication {
    @Inject
    LocalChatRoutes.Client client;

    @Override
    public int run(String... args) throws Exception { 
        LocalChatRoutes.SessionBuilder builder = client.builder()
                                                       .messageHandler(message -> System.out.println(message))
                                                       .errorHandler(message -> System.err.println(message));

        LocalChatRoutes.Session session = builder.connect("RagBot");

        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String userMessage = scanner.nextLine().trim();
            if (userMessage.isEmpty()) continue;

            session.chat(userMessage);
        }
        
        return 0;
    }
}
```

The `ChatApp` injects a `Client` instance.  The `run()` method first creates a `SessionBuilder` and registers
some event handlers.  We'll discuss events more later, but the helper method `messageHandler()` registers a handler that just consumes string "Message" events.
Here's we're just registering `System.out.println`.  Same for error events.  The `SessionBuiler.connect()` method estiblishes a session and
specifies that the initial chat route will be `RagBot`.  Chat messages will be routed to `RagBot.chat()`.

So, as the client excepts command line input, it sends that string as a user message to the chat route service which determines that the current route is `RagBot`
so it invokes on the `RagBot.chat()` method.  The `RagBot.chat()` method returns a `String` which automatically gets converted into a `Message` event and sent
to the session.  The `Session` has a registered `Message` handler which outputs to `System.out`.

Here's the full local client API:

```java
/**
 * Interfaces for making local chat route invocations.
 */
public interface LocalChatRoutes {

    public interface Session extends AutoCloseable {

        public static final String USER_MESSAGE_KEY = "userMessage";

        /**
         * Invoke current chat route and wait until it is finished
         */
        default void chat() {
            chat(new HashMap<>());
        }

        /**
         * Invoke current chat route and wait until it is finished
         *
         * @param userMessage
         */
        void chat(String userMessage);

        /**
         * Invoke current chat route and wait until it is finished
         * The user message can be sent to the chat route as a {@link #USER_MESSAGE_KEY} key in the message map.
         * The message map maps to the parameter names of the chat route method.
         *
         * @param message
         */
        void chat(Map<String, Object> message);

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
         * @return
         */
        default Promise<Void> chatPromise() {
            return chatPromise(new HashMap<>());
        }

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
         *
         * @param message
         */
        Promise<Void> chatPromise(String userMessage);

        /**
         * Invoke current chat route, using a promise to handle the result asynchronously
        * The user message can be sent to the chat route as a {@link #USER_MESSAGE_KEY} key in the message map.
         * The message map maps to the parameter names of the chat route method.
         *
         * @param message
         */
        Promise<Void> chatPromise(Map<String, Object> message);

        void close();
    }

    public interface SessionBuilder {
        /**
         * Register an event handler for a specific event type.
         *
         * @param eventType
         * @param handler
         * @return
         */
        <T> SessionBuilder eventHandler(String eventType, Consumer<T> handler);

        /**
         * Register an event handler for error events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.ERROR, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder errorHandler(Consumer<String> handler);

        /**
         * Register an event handler for message events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.MESSAGE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder messageHandler(Consumer<String> handler);

        /**
         * Register an event handler for console events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.CONSOLE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder consoleHandler(Consumer<String> handler);

        /**
         * Register an event handler for thinking events.
         * This is shorthand for {@code eventHandler(ChatRouteConstants.THINKING, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder thinkingHandler(Consumer<String> handler);

        /**
         * Register an event handler for stream events. Stream events are used to stream the chat response to the client i.e.
         * when a @ChatRoute method returns a Multi<String>
         * This is shorthand for {@code eventHandler(ChatRouteConstants.STREAM, handler)}
         * 
         *
         * @param handler
         * @return
         */
        SessionBuilder streamHandler(Consumer<String> handler);

        /**
         * If there are no event handlers registered for an event type, the default handler will be called.
         * The default handler will be called with the event type and the event data.
         *
         * @param handler
         * @return
         */
        SessionBuilder defaultHandler(BiConsumer<String, Object> handler);

        /**
         * Start a session with the event buss using the initial route as the chat route
         *
         * @param initialRoute
         * @return
         */
        Session connect(String initialRoute);

        /**
         * Start a session with the event buss using the default route as the chat route
         *
         * @return
         */
        Session connect();
    }

    /**
     * A CDI session scope is created per client.
     *
     * @return
     */
    interface Client extends AutoCloseable {

        @Override
        void close();

        SessionBuilder builder();
    }

    Client newClient();

}
```

## Default chat routes `@DefaultRoute`

If a client doesn't specify the initial chat route when it creates a session, then requests will fail
unless the appliation has a `@DefaultRoute` defined.  Only one default route per application is allowed.

```java
@RegisterAiService 
@ChatScoped
public interface RagBot {

    @ChatRoute("RagBot")
    @DefaultRoute
    @SystemMessage("""
            You are an AI named Bob answering questions about financial products.
            Your response must be polite, use the same language as the question, and be relevant to the question.

            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    String chat(@UserMessage String question);
}
```

## Events

Events are defined as a string type name and data.  Chat routes defines some convenience built-in event types which you as the developer can ignore if you want.

* "Message" - Regular string responses from the server.
* "ObjectMessage" - Arbitrary POJOs sent back to the client (in Websocket case, they are marshalled as JSON)
* "Thinking" - LLM thinking message, or messages you might want to send to the client while processing is happening
* "Console" - Useful for browser apps that want to output to the browser console
* "Error" - Application errors

You can define your own event types.  Just choose the type name and data format you want.  Can be anything.

##  Mapping AiService method return values to events

`@ChatRoute` method return values are mapped to an event type as follows:

* `String` return values map to the `Message` event type by default.  
* Any other type is mapped to `ObjectMessage` event type by default
* `void` return values do not send any message back to the client.
* If the return value is null no message is sent.
* If the return value is `Multi<String>`, then you are streaming LLM responses.  For each
chunk of the stream a `Stream` event will be sent back to the client.
* If the return value is a `dev.langchain4j.service.Result`, the even type
is calculated from the `Result.content` if it is not null.  If the content is null,
then the tool executions are looped through and an event is sent for each non-null `ToolExecution.resultObject`

### `@EventType` annotation

You can place the `@EventType` annotation on a class or a `@ChatRoute` method.  
The `@EventType` allows you to override the default event type mapping of the return value.

here's some examples:

```java

@EventType("customer")
public class Customer {}

@ChatScoped
public class MyRoutes {
    @EventType("MyEventType")
    @ChatRoute("chat-one")
    public String chatOne() {...}

    @ChatRoute("customer-chat")
    public customer chatCustomer() {...}
}
```

## Sending events from service back to client

During the processing of a chat request, your chat routes can send back events to the client asynchronously, whenever they want.  This is done through the `ChatRouteContext` interface.

```java
package io.quarkiverse.langchain4j.chatscopes;

import java.lang.reflect.Type;

/**
 * This interface is used on the server to represent a chat route invocation.
 */
public interface ChatRouteContext {

    public interface Request {
        /**
         * Shorthand for {@code data("userMessage", String.class)}
         *
         * @return
         */
        String userMessage();

        /**
         * Get a data value from the request.
         * @param <T>
         * @param key
         * @param type
         * @return
         */
        <T> T data(String key, Type type);
    }

    public interface ResponseChannel {
        /**
         * Send an event to the client.
         *
         * @param event
         * @param data
         */
        void event(String eventType, Object data);

        /**
         * Send an event to the client.  The event type will be determined by the data class.
         * The data class must have a @EventType annotation so the event type can be determined.
         * 
         * @param data
         */
        default void event(Object data) {
            if (data == null) {
                throw new IllegalArgumentException("event argument cannot be null");
            }
            EventType eventType = data.getClass().getAnnotation(EventType.class);
            if (eventType != null) {
                event(eventType.value(), data);
            } else {
                throw new IllegalArgumentException("Unable to determine event type. " + data.getClass().getName()
                        + " does not have a @EventType annotation");
            }
        }

        /**
         * Convenience method for sending a built in error event type.  Sends a string error message to the client
         * This is shorthand for {@code event(ChatRouteConstants.ERROR, msg)}
         *
         * @param msg
         */
        default void error(String msg) {
            event(ChatRouteConstants.ERROR, msg);
        }

        /**
         * Convenience method for sending a built in thinking event type.  Sends a string thinking message to the client
         * This is shorthand for {@code event(ChatRouteConstants.THINKING, msg)}
         *
         * @param msg
         */
        default void thinking(String msg) {
            event(ChatRouteConstants.THINKING, msg);
        }

        /**
         * Convenience method for sending a built in console event type.  Sends a string console message to the client
         * This is shorthand for {@code event(ChatRouteConstants.CONSOLE, msg)}
         *
         * @param msg
         */
        default void console(String msg) {
            event(ChatRouteConstants.CONSOLE, msg);
        }

        /**
         * Convenience method for sending a built in message event type.  Sends a string message to the client
         * This is shorthand for {@code event(ChatRouteConstants.MESSAGE, msg)}
         *
         * @param msg
         */
        default void message(String msg) {
            event(ChatRouteConstants.MESSAGE, msg);
        }
    }

    Request request();

    ResponseChannel response();

}
```

The `ChatRouteContext.ResponseChannel` interface is used to send back events to the client.  Each event type is designated by a string and the body of the event is any Java object.  Some predefined
event types are available (i.e. message, thinking, and error events).  Server code just needs to `@Inject` the `ChatRouteContext` and can send back messages to the client anytime during chat request processing.  For instance, maybe you want to send back a _thinking_ message back to the client while within LLM tool execution so that the user gets some feedback while waiting for the LLM to respond.  Another example is maybe you don't like the plain text response the LLM responds with and you want to add some formatting. Here's an example:

```java
@ChatScoped
public class MyToolbox {
   @Inject
   ChatRouteContext ctx;


   @Tool("Convert from english to a mathematical formula")
   String convert(String english) {
        ctx.response().thinking("Creating the math formula from your prompt.  Might take a little bit...");
        return prompt.convert(english);

   }
}
```

## Changing Current Chat Route

Chat requests are always routed to the current chat route associated with the current chat scope.  There are a couple
of ways that you can change the current chat route.

* Calling `ChatRoutes.route(String route)` changes the current route for the current chat scope
* Calling `ChatScope.push(String route)` is shorthand for `ChatScope.push(); ChatRoutes.route(String route)`
* Calling `ChatScope.pop()`.  This not only pops and reverts the current chat scope to the parent scope, but also reverts to the route associated with the parent chat scope

Here's an example of each:

```java
import io.quarkiverse.langchain4j.chatscopes.ChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;

@ApplicationScoped
public MyRoute {
     
     @ChatRoute("one")
     public void chat1() {
        // current chat scope route is set to "two"
        ChatRoutes.route("two");
     }

     @ChatRoute("two")
     pubic void chat2() {
        // shorthand for ChatScope.push(); ChatRoutes.route("nested")
        ChatScope.push("nested");
     }

     @ChatRoute("nested")
     public void nested() {
        // chat route is back to "two" as "two" is associated with the parent chat scope.
        ChatScope.pop();
     }
}
```

## Websocket Chat Routes

You can invoke on chat routes using Web Sockets.  Simply include the quarkus extension for it

```xml
<dependency>
   <groupId>io.quarkiverse.langchain4j</groupId>
   <artifactId>quarkus-langchain4j-chat-scopes-websocket</artifactId>
</dependency>
```

The URL for the web socket endpoint is `/_chat/routes`.  All communication with clients are throw
JSON text messages so any event Java classes must be marshallable to JSON.  Jackson is used
under the covers so you can tailor your event classes using Jackson annotations and APIs.

### `@MarkdownToHtml`

A lot of LLMs output markdown by default.  A convenience annotation `@Markdown

## Websocket Javascript client

Websocket chat routes automatically come with a javascript client whose code lives under this URL:

`/_chat/javascript/chatscopes.js`

Simply include it as a script

```html
<script src="/_chat/javascript/chatscopes.js"></script>
```

Here's how it looks:

```javascript
client = new ChatScopesClient();
await client.open("ws://localhost:8080/_chat/routes");

builder = client.builder();
builder.messageHandler(handleMessage);
session = await builder.connect("theRoute");

session.chat("hello world);

function handleMessage(data) {
    // output string message from server
    console.log("Message: " + data);
}
```

Here's the full javascript API.  The API tries to mirror the local java one.

```javascript

// returns a Promise.  userMessage is just a string
Session.chat(userMessage)

// returns a Promise.  Data must be an object
Session.sendData(data)

// SessionBuilder methods

// eventType is a string, handler is a function that takes one parameter
// The handler is called if the server sends back an event
// that matches the eventType
SessionBuilder.eventHandler(eventType, handler)

// Shorthand for SessionBuilder.eventHandler('Error', handler)
SessionBuilder.errorHandler(handler)

// Shorthand for SessionBuilder.eventHandler('Message', handler)
SessionBuilder.messageHandler(handler)

// Shorthand for SessionBuilder.eventHandler('Console', handler)
SessionBuilder.consoleHandler(handler)

// Shorthand for SessionBuilder.eventHandler('Thinking', handler)
SessionBuilder.thinkingHandler(handler)

// When streaming back LLM responses through a Multi<String>
// the server will send back a chunk for each substring of the
// the stream.  
// The handler is a function with one argument that is a string
SessionBuilder.streamHandler(handler)

//  The handler is a function that has two parameters 
// (eventType, data)
//
// This handler is called whenever there is no
// handler registered for a specific event type
SessionBuilder.defaultHandler(handler)

// create a session with the server with no initial chat route specified
// returns a promise.  The promise wraps a Session object
SessionBuilder.connect()

// create a session with the server with an initial chat route specified
// returns a promise.  The promise wraps a Session object
SessionBuilder.connect(route)

// Start the websocket connection
// returns a Promise when connected
ChatScopesClient.open(url)

// Creates a SessionBuilder object
ChatScopesClient.builder()

// Websocket instance if you need to register
// onClose event or anything else related to the websocket
ChatScopesClient.websocket
```

## Websocket Java client

A Websocket Java client comes with the `quarkus-langchain4j-chat-scopes-websocket` extension.  It looks
almost exactly like the local client API.  Let's change the local client example shown in that chapter to use
websockets instead:

```java
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes;
import java.util.Scanner;

@QuarkusMain
public class ChatApp implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        // create websocket and connect 
        BasicWebSocketConnector connector = BasicWebSocketConnector.create();
        connector.baseUri("http://localhost:" + getTestPort() + "/_chat/routes");
        WebsocketChatRoutes.Client client = WebsocketChatRoutes.newClient(connector, objectMapper);

        // register event handlers with SessionBuilder
        client.builder()
              .messageHandler(message -> System.out.println(message))
              .errorHandler(message -> System.err.println(message));

        WebsocketChatRoutes.Session session = builder.connect("RagBot");

        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String userMessage = scanner.nextLine().trim();
            if (userMessage.isEmpty()) continue;

            session.chat(userMessage);
        }
        
        return 0;
    }
}
```

Since the communication with the server
uses JSON text messages, the `JsonEvent` interface is used instead of raw `java.lang.Object` instances for non-string
event types. 

```java
    /**
     * Events are sent back as json to the client.  This interface is used
     * to get at the raw jason data, or to deserialize it into a specific type.
     */
    interface JsonEvent {
        /**
         * Deserialize the event from JSON into a specific type.
         * 
         * @param <T>
         * @param type
         * @return
         */
        <T> T get(Type type);

        /**
         * Get the raw JSON for the event.
         *
         * @return
         */
        JsonNode raw();
    }
```



Here's an example:


```java
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes;
import java.util.Scanner;

record Customer(String name, String address);

@QuarkusMain
public class ChatApp implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        // create websocket and connect 
        BasicWebSocketConnector connector = BasicWebSocketConnector.create();
        connector.baseUri("http://localhost:" + getTestPort() + "/_chat/routes");
        WebsocketChatRoutes.Client client = WebsocketChatRoutes.newClient(connector, objectMapper);

        // register event handlers with SessionBuilder
        WebsocketChatRoutes.SessionBuilder builder = client.build()
              .messageHandler(System.out.println)
              .errorHandler(System.err.println);

        // register custom event handler that uses JsonEvent
        builder.eventHandler("customer-event", event -> {
            Customer customer = event.get(Customer.class);
            System.out.println(customer.name());
        })
...
    }
}
```

The full websocket client api looks like this:

```java
/**
 * Interfaces for making websocket chat route invocations.
 */
public interface WebsocketChatRoutes {
    /**
     * Sessions are not thread-safe.
     * 
     * Sessions represent a single chat conversation with the server.
     */
    public interface Session extends AutoCloseable {
        /**
         * Invoke current chat route and wait until it is finished.
         * The current chat route of the chat scope conversation will be invoked on the server.
         */
        default void chat() {
            chat(new HashMap<>());
        }

        /**
         * Invoke current chat route and wait until it is finished
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param userMessage
         */
        void chat(String userMessage);

        /**
         * Invoke current chat route and wait until it is finished
         * The message parameter is marshalled to JSON and sent to the server.
         * The top level JSON properties of the message object are used as the parameters of the chat route method.
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         * @param message
         */
        void chat(Object message);

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         * @return
         */
        default Promise<Void> chatPromise() {
            return chatPromise(new HashMap<>());
        }

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         * @param userMessage
         * @return
         */
        Promise<Void> chatPromise(String userMessage);

        /**
         * Invoke current chat route using a Promise to handle the result asynchronously
         * The message parameter is marshalled to JSON and sent to the server.
         * The top level JSON properties of the message object are used as the parameters of the chat route method.
         *
         * The current chat route of the chat scope conversation will be invoked on the server.
         *
         * @param message
         * @return
         */
        Promise<Void> chatPromise(Object message);

        /**
         * Closes the chat session which will clean up any CDI chat scoped beans associated with this session.
         */
        void close();
    }

    /**
     * Events are sent back as json to the client.  This interface is used
     * to get at the raw jason data, or to deserialize it into a specific type.
     */
    interface JsonEvent {
        /**
         * Deserialize the event from JSON into a specific type.
         * 
         * @param <T>
         * @param type
         * @return
         */
        <T> T get(Type type);

        /**
         * Get the raw JSON for the event.
         *
         * @return
         */
        JsonNode raw();
    }

    public interface SessionBuilder {
        /**
         * Register an event handler for a specific event type.
         *
         * @param eventType
         * @param handler
         * @return
         */
        SessionBuilder eventHandler(String eventType, Consumer<JsonEvent> handler);

        /**
         * Register an event handler for message events.  This is shorthand for {@code eventHandler(ChatRouteConstants.MESSAGE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder messageHandler(Consumer<String> handler);

        /**
         * Register an event handler for error events.  This is shorthand for {@code eventHandler(ChatRouteConstants.ERROR, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder errorHandler(Consumer<String> handler);

        /**
         * Register an event handler for console events.  This is shorthand for {@code eventHandler(ChatRouteConstants.CONSOLE, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder consoleHandler(Consumer<String> handler);

        /**
         * Register an event handler for thinking events.  This is shorthand for {@code eventHandler(ChatRouteConstants.THINKING, handler)}
         *
         * @param handler
         * @return
         */
        SessionBuilder thinkingHandler(Consumer<String> handler);

        /**
         * Register an event handler for stream events.  This is shorthand for {@code eventHandler(ChatRouteConstants.STREAM, handler)}
         * Each stream event is a chunk sent by the server, i.e. if a chat route returns a Multi<String>
         *
         * @param handler
         * @return
         */
        SessionBuilder streamHandler(Consumer<String> handler);

        /**
         * Register an event handler for default events.  This is called if no event handler is registered for the event type.
         *
         * @param handler
         * @return
         */
        SessionBuilder defaultHandler(BiConsumer<String, JsonEvent> handler);

        /**
         * Connect to the server using the initial route.
         *
         * @param initialRoute
         * @return
         */
        Session connect(String initialRoute);
        
        /**
         * Connect to the server using the default route.
         *
         * @return
         */
        Session connect();
    }

    /**
     * Represents a single websocket connection to the server.
     */
    interface Client extends AutoCloseable {
        /**
         * Closes the websocket connection to the server.
         */
        @Override
        void close();

        SessionBuilder builder();
    }

    static Client newClient(BasicWebSocketConnector connector, ObjectMapper objectMapper) {
        return new ChatRouteClient(connector, objectMapper);
    }

    /**
     * Create a new client using CDI to look up the client's ObjectMapper.
     *
     * @param connector
     * @return
     */
    static Client newClient(BasicWebSocketConnector connector) {
        return newClient(connector, Arc.container().instance(ObjectMapper.class).get());
    }
}
```

## Securing chat routes

If using websockets, just secure using [Quarkus Websocket Next mechanisms](https://quarkus.io/guides/websockets-next-reference#websocket-next-security).

Place security annotations like `@RolesAllowed` on your `@ChatRoute` methods as needed.

# New APIs for managing chat memory

There's some new APIs for managing chat memory for your `@ChatScoped` and `@PerChatScoped` AiServices.

```java
public interface ChatScopeMemory {

    /**
     * Clears chat memory for chat scoped beans in the current chat scope only.
     */
    static void clearMemory() {
    }

    /**
     * Schedule a wipe of chat memory for chat scoped beans in the current chat scope
     * Wipe will be performed just before the CDI request context is terminated.
     */
    static void scheduleWipe() {
    }

    /**
     * Abort a scheduled wipe of chat memory for chat scoped beans in the current chat scope.
     * An abort cannot be canceled.
     */
    static void abortWipe() {
    }

    /**
     * Checks if a wipe of chat memory is scheduled for chat scoped beans in the current chat scope.
     *
     * @return true if a wipe is scheduled, false otherwise
     */
    static boolean wipeScheduled() {
    }

}
```

Call `clearMemory()` anytime and it will immediately clear any chat memory for any chat scoped AiService accessed within the current
chat scope.

The `scheduleWipe()` is useful when a `@Tool` method decides that chat memory should be cleared for the current chat scope.  You don't
want to clear chat memory immediately within tool executions because the chat request and LLM need to know the history of tool invocations
and such within the current chat request.  Instead, you `scheduleWipe()`.  This will clear chat memory for the current chat scope when the
full AiService method call (the chat request) is complete.

Another tool method call within a chat request might need to make sure that chat memory is never wiped, so it can call `abortWipe()` to ensure
that no wipe ever happens within the chat request.

# Example Patterns for using Chat Scopes

## Main Menu pattern

The _Main Menu Pattern_ is when you have a top level conversation that decides, based on the user message, what type of conversation the user wants to have
and routes the user to a new conversation.  If you have a chat app that has a broad set of functionality it's pretty hard to define a prompt that covers all 
this functionality.  You also don't want to have an ever-expanding list of tools the LLM has to decide on.  It might cause the LLM to hallucinate, and sending this large 
toolset to the LLM for each chat request just eats up tokens.  The _Main Menu Pattern_ allows your application to narrow the conversation with the LLM so that you can 
have precise and smaller prompts and toolsets.  Here's how it looks with chat scopes.

Consider the often used travel agent example.  If you were going on a vacation to Ireland, you'd want to book a flight, hotel room, and car.  You might
want to search for tours available in the country and then book them.  Each one of these things is a separate conversation the LLM and human will want to have.
You can use the _Main Menu Pattern_ to navigate to the conversation (chat route and scope) you want to have.

```java
@ChatScoped
public interface MainMenuPrompt {
    @SystemMessage("""You are a travel agent.  Help the user to book flights, hotel rooms, and rent a car.  They can also search for tours available at their
    destination and book them.""")
    @ChatRoute("mainMenu")
    @DefaultRoute
    @ToolBox(MainMenu.class)
    public String bookings(@UserMessage userMessage);
}
```

The `MainMenu` toolbox class is used to route to the conversation you want to have.

```java
@ChatScoped
public class MainMenu {

    @Tool("Book a flight")
    public void bookFlight() {
        ChatScope.push("flight");
        ChatRoutes.execute();
    }

    @Tool("Book a hotel")
    public void bookHotel() {
        ChatScope.push("hotel");
        ChatRoutes.execute();
    }

    @Tool("Ask about and book tours at the location you want")
    public void searchTours() {
        ChatScope.push("tours");
        ChatRoutes.execute();
    }
}
```

Consider a user message `I want to book a flight to Ireland in July`.  The `MainMenuPrompt.book()` AiService method would be called
and the LLM would decide to invoke the `MainMenu.bookFlight()` tool method.  This method starts a new chat scope
conversation to book a flight by executing `ChatScope.push("flight")`.  This method starts a new child chat scope and
sets the current chat route to be _flight_.  

The user message has data is needed to book the flight:  The user wants to go to Ireland in July.
Because the original user message might have data that's interesting to the _flight_ chat route, we want to execute it right away with the current user message.  This is why the `ChatRoutes.execute()` method is called.  `execute()` will invoke the current chat route using the same user message.

```java
@ChatScoped
public interface FlightPrompt {
    @SystemMessage("""You are an airline reservation system.  Converse with the user to book their flight.""")
    @ChatRoute("flight")
    @DefaultRoute
    @ToolBox(Flight.class)
    public String bookFlight(@UserMessage userMessage);
}
```

When the `flight` chat route is done it should call `ChatScope.pop()` within one of it's tool methods.  This will clean up any state and chat memory associated with
the scope and revert to the `mainMenu` route and parent scope.  Future user messages would then be routed back to the `mainMenu` route.

### Using `IMMEDIATE` Tools to speed things up

There's a big optimization you can do for the _Main Menu Pattern_.  Tool invocation responses are looped back with the LLM to let the LLM do more stuff.
For the _Main Menu Pattern_ we are usually handing off processing to a new prompt and chat route.  Therefore we can end the initial LLM conversation immediately, speed
up our invocation processing, and reduce our token consumption with the LLM.  This is where an LC4J feature called `IMMEDIATE` tools fits in perfectly as a solution.

We first modify our `MainMenuPrompt` to return a `dev.langchain4j.service.Result`.

```java
@ChatScoped
public interface MainMenuPrompt {
    @SystemMessage("""You are a travel agent.  Help the user to book flights, hotel rooms, and rent a car.  They can also search for tours available at their
    destination and book them.""")
    @ChatRoute("mainMenu")
    @DefaultRoute
    @ToolBox(MainMenu.class)
    public Result<String> bookings(@UserMessage userMessage);
}
```

Then we modify our `MainMenu` tool methods to force an immediate return from the tool method.

```java
import dev.langchain4j.agent.tool.ReturnBehavior;

@ChatScoped
public class MainMenu {

    @Tool(value="Book a flight", , returnBehavior = ReturnBehavior.IMMEDIATE)
    public void bookFlight() {
        ChatScopeMemory.clearMemory();
        ChatScope.push("flight");
        ChatRoutes.execute();
    }

... repeat for other tools ...
}
```

We don't need the chat history for the main menu, so we just clear it.  Adding `ReturnBehavior.IMMEDIATE` will end the current LC4J chat request.

## Builder/Wizard nested chats

For our travel agent app that we started in the previous chapter, there's a bunch of nested conversations we need to have with the LLM to build up or create various things.
Let's take a flight reservation for instance.  Chat scoped beans are a great place to store application state as you build things by chatting with the LLM.

Let's look at our flight reservation AiService again:

```java
@ChatScoped
public interface FlightPrompt {
    @SystemMessage("""You are an airline reservation system.  Converse with the user to book their flight.
        Gather departure and return flight dates, destination, and ticket class before suggesting a booking.
    """)
    @ChatRoute("flight")
    @DefaultRoute
    @ToolBox(Flight.class)
    public String bookFlight(@UserMessage userMessage);
}
```

We can store the building of our reservation within a POJO in our `Flight` toolbox class.

```java
@ChatScoped
public class Flight {
    public enum TicketClass {
        economy,
        business,
        first
    }
    public static class ReservationDetails {
        public Date departure;
        public Date returnDate;
        public String destination;
        public TicketClass ticketClass;

    }

    @Inject
    ChatRouteContext ctx;

    ReservationDetails reservation = new ReservationDetails();

    
    @Tool("specify the departure date")
    public void setDeparture(Date date) {
        ctx.response().thinking("Set departure date to: " + date);
        reservation.departure = date;
    }

    @Tool("specify the departure date")
    public void setReturnDate(Date date) {
        ctx.response().thinking("Set retirm date to: " + date);
        reservation.returnDate = date;
    }
... other setters ...

    @Tool("Book the reservation")
    public void book() {
        verifyReservation(reservation);
        makeBooking(reservation);

        ChatScope.pop();
    }
    
}
```

Make the toolbox class be a chat scoped bean.  Store the state in a pojo in that bean.  Have `@Tool` setter methods build up the state.
The `book()` tool method makes the booking and ends the nested conversation.


## One-off LLM invocations

AiServices that are one-off, one-time invocations on the LLM should be defined as `@InvocationScoped` beans.  Traditionally, `@RequestScoped` AiServices
were used, but if you called request scoped AiServices within the same request, you the chat memory of the previous request would be added to the 2nd invocation.
Besides eating up precious tokens, this old chat memory can mess up LLM results.  

For example, let's say you had an AiService that converted natural language into a SQL query.  Might be best to reset chat memory per invocation:

```java
@InvocationScoped
public interface SqlGenerator {
    @SystemMessage("""Based on this schema: {schema} generate a SQL query from the natural language user message""")
    String buildQuery(String schema, @UserMessage userMessage);
}
```

# Links to more sophisticated examples
