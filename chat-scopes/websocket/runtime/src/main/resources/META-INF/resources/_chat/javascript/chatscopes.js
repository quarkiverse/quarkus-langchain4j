class Session {
    constructor(builder, id) {
        this.client = builder.client;
        this.id = id;
        this.eventHandlers = builder.eventHandlers;
        this.defaultHandler = builder.defaultHandler;
        this.eventHandlers.set("Completed", (message) => {
            console.log("Completed: " + message);
            this.currentScope = message;
            let future = this.future;
            if (future) {
                this.future = null;
                future.resolve();   
            }
        });
        this.eventHandlers.set("Failed", (message) => {
            console.log("Failed: " + message); 
            let future = this.future;
            if (future) {
                this.future = null;
                future.reject(new Error(message));
            }
        });
    }

    chat(userMessage) {
        let data = {
            userMessage: userMessage
        }
        return this.sendData(data);
    }

    sendData(data) {
        return this.sendEvent("ChatRouteMessage", data);
    }

    sendEvent(type, data) {
        console.log("Sending event: " + type);
        let msg = {
            type: type,
            chatId: this.id,
            scopeId: this.currentScope,
            data: data
        }
        return new Promise((resolve,reject) => {
            this.future = { resolve, reject };
            this.client.send(msg);
        });
    }


}


class SessionBuilder {

    constructor(client) {
        this.client = client;
        this.eventHandlers = new Map();
        this.consoleHandler((message) => {
            console.log(message);
        });
        this.thinkingHandler((message) => {
            console.log(message);
        });
        this.eventHandlers.set("SystemError", (message) => {
            console.log("SystemError: " + message);
        });
        this.eventHandlers.set("Message", (message) => {
            console.log("Message: " + message);
        });
    }

    eventHandler(eventType, handler) {
        this.eventHandlers.set(eventType, handler);
        return this;
    }

    errorHandler(handler) {
        this.eventHandlers.set("Error", handler);
        return this;
    }

    messageHandler(handler) {
        this.eventHandlers.set("Message", handler);
        return this;
    }

    consoleHandler(handler) {
        this.eventHandlers.set("Console", handler);
        return this;
    }

    thinkingHandler(handler) {
        this.eventHandlers.set("Thinking", handler);
        return this;
    }

    streamHandler(handler) {
        this.eventHandlers.set("Stream", handler);
        return this;
    }

    objectHandler(handler) {
        this.eventHandlers.set("Object", handler);
        return this;
    }

    defaultHandler(handler) {
        this.defaultHandler = handler;
        return this;
    }

    connect() {
        return this.connect(null);
    }

    connect(route) {
        let id = this.client.counter + '-' + Date.now();
        this.client.counter++;
        let newSession =  new Session(this, id);
        this.client.sessions.set(id, newSession);
        let msg = {
            type: "Connect",
            chatId: id,
            route: route
        }

        return new Promise((resolve, reject) => {
            let resolveSession = () => {
                resolve(newSession);
            }
            newSession.future = {
                resolve: resolveSession,
                reject: reject
            }
            this.client.send(msg);
        });
    }
}

class ChatScopesClient {

    constructor() {
        this.sessions = new Map();
        this.counter = 0;
    }

    open(endpoint) {
        this.websocket = new WebSocket(endpoint);
        this.websocket.onmessage = (event) => {
            this.onMessage(event);
        };
        this.websocket.onclose = () => {
            console.log("WebSocket connection closed");
        };
        return new Promise((resolve) => {
            if (this.websocket && this.websocket.readyState !== this.websocket.OPEN) {
              this.websocket.addEventListener('open', () => { resolve() });
            } else {
              resolve();
            }
        });
    }

    onMessage(event) {
        console.log("Received message: " + event.data);
        const message = JSON.parse(event.data);
        const chatId = message.chatId;
        const session = this.sessions.get(chatId);
        if (!session) {
            console.log("Session not found for chatId: " + chatId);
            return;
        }
        const eventType = message.type;
        const eventData = message.data;
        const handler = session.eventHandlers.get(eventType);
        if (handler) {
            handler(eventData);
        } else {
            if (session.defaultHandler) {
                session.defaultHandler(eventType, eventData);
            } else {
                console.log("Handler not found for eventType: " + eventType);
            }
        }
    }

    builder() {
        return new SessionBuilder(this);
    }

    send(message) {
        this.websocket.send(JSON.stringify(message));
    }

}

