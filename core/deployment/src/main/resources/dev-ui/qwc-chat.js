import {LitElement, html, css} from 'lit';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/checkbox';
import '@vaadin/details';
import '@vaadin/vertical-layout';
import '@vaadin/message-input';
import '@vaadin/message-list';
import '@vaadin/progress-bar';
import '@vaadin/text-field';
import '@vaadin/icon';
import '@vaadin/icons';
import { JsonRpc } from 'jsonrpc';
import { systemMessages } from 'build-time-data';

export class QwcChat extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
            margin-left: 15px;
            margin-right: 15px;
        }

        .systemMessagePane {
            display: flex;
            padding: var(--lumo-space-s) var(--lumo-space-m);
            gap: 10px;
        }

        .systemMessageInput {
            width: 100%;
        }

        .systemMessage {
            background: var(--lumo-contrast-10pct);
        }

        .errorMessage {
            background: var(--lumo-error-color-50pct);
        }

        .hide {
            visibility: hidden;
        }
        .show {
            visibility: visible;
        }    
        
        .remove {
            display: none;
        }
        .add {
            display: block;
        }

    `;

    static properties = {
        _unfilteredChatItems: {state: true},
        _chatItems: {state: true},
        _progressBarClass: {state: true},
        _newConversationButtonClass: {state: true},
        _systemMessage: {state: true},
        _systemMessages: {state: true},
        _systemMessageDisabled: {state: true},
        _ragEnabled: {state: true},
        _showToolRelatedMessages: {state: true}
    }

    constructor() {
        super();
        this._showToolRelatedMessages = true;
        this._ragEnabled = true;
        this._systemMessages = systemMessages;
        this._systemMessage = systemMessages.length == 1 ? systemMessages[0] : "";
        this._hideProgressBar();
        this._beginInputOfNewSystemMessage();
        this._unfilteredChatItems = [];
        this._chatItems = [];
        this.jsonRpc.reset({systemMessage: this._systemMessage});
    }

    render() {
        this._filterChatItems();
        return html`
            <div><vaadin-checkbox checked label="Show tool-related messages"
                                  @change="${(event) => {
                                      this._showToolRelatedMessages = event.target.checked;
                                      this.render();
                                  }}"/></div>
            <div><vaadin-checkbox checked label="Enable Retrieval Augmented Generation (if a RetrievalAugmentor bean exists)"
                                  @change="${(event) => {
                                      this._ragEnabled = event.target.checked;
                                      this.render();
                                  }}"/></div>
            <vaadin-select
                    label="Select a sample system message"
                    @change="${(event) => {
                        this._systemMessage = event.target.value.systemMessage;
                        this.shadowRoot.querySelector('.userMessageInput').value = event.target.value.userMessage;
                        this._enableSystemMessageInputField();
                        this._cementSystemMessage();
                        this.render();
                    }}"
                    .items="${this._sampleSystemMessages}"
                    .value="${this._sampleSystemMessages[0].value}"
            ></vaadin-select>
            ${this._renderSystemPane()}
            <vaadin-message-list .items="${this._chatItems}"></vaadin-message-list>
            <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
            <vaadin-message-input class="userMessageInput" @submit="${this._handleSendChat}"></vaadin-message-input>
        `;
    }

    _renderSystemPane(){
        return html`<div class="systemMessagePane">
            <vaadin-button class="${this._newConversationButtonClass}" @click="${this._beginInputOfNewSystemMessage}">Start a new conversation</vaadin-button>
            <vaadin-text-field class="systemMessageInput"
                    placeholder="(Optional). Changing this will start a new conversation"
                    @keypress="${this._checkForEnterOrTab}" 
                    @focusout="${this._checkForEnterOrTab}"
                    @input="${this._populateSystemMessage}" 
                    value="${this._systemMessage}" 
                    ?disabled=${this._systemMessageInputFieldDisabled}>
                    <span slot="prefix">System message: </span> 
            </vaadin-text-field>
            </div>`;
    }

    _checkForEnterOrTab(e){
        if ((e.which == 13 || e.which == 0)){
            this._cementSystemMessage();
            this.shadowRoot.querySelector('.systemMessageInput').focus();
        }
    }

    _populateSystemMessage(e){
        if(e.target.value.trim() === ''){
            this._systemMessage = "";
        }else{
            this._systemMessage = e.target.value;
        }
    }

    _beginInputOfNewSystemMessage(){
        this._enableSystemMessageInputField();
        this._hideNewConversationButton();
        this._clearHistory();
    }

    _cementSystemMessage() {
        if (!this._systemMessageInputFieldDisabled) {
            this._disableSystemMessageInputField();
            this._showNewConversationButton();
            this._clearHistory();
            if (this._systemMessage && this._systemMessage.trim().length > 0) {
                this._addSystemMessage(this._systemMessage);
            }
            this.jsonRpc.reset({systemMessage: this._systemMessage});
        }
    }

    _handleSendChat(e) {
        let message = e.detail.value;
        if(message && message.trim().length>0){
            this._cementSystemMessage();
            this._addUserMessage(message);
            this._showProgressBar();

            this.jsonRpc.chat({message: message, ragEnabled: this._ragEnabled}).then(jsonRpcResponse => {
                this._showResponse(jsonRpcResponse);
            }).catch((error) => {
                this._showError(error);
                this._hideProgressBar();
            });
        }

      }

    _showResponse(jsonRpcResponse) {
        if (jsonRpcResponse.result === false) {
            // the JsonRPC method threw an exception, this should generally
            // not happen, but just in case...
            this._showError(jsonRpcResponse);
        } else {
            if (jsonRpcResponse.result.error) {
                this._showError(jsonRpcResponse.result.error);
            } else {
                this._processResponse(jsonRpcResponse.result.history);
            }
        }
        this._hideProgressBar();
    }

    _showError(error) {
        var errorString = JSON.stringify(error);
        if(errorString === '{}') {
            // assume the error is a string
            errorString = error;
        }
        this._addErrorMessage(errorString);
    }

    _processResponse(items) {
        this._unfilteredChatItems = [];
        items.forEach((item) => {
            if(item.type === "AI") {
                if(item.message) {
                    this._addBotMessage(item.message);
                }
                if(item.toolExecutionRequests) {
                    var toolMessage = "Request to execute the following tools:\n";
                    item.toolExecutionRequests.forEach((toolExecutionRequest) => {
                        toolMessage += `Request ID = ${toolExecutionRequest.id}, 
tool name = ${toolExecutionRequest.name}, 
arguments = ${toolExecutionRequest.arguments}\n`;
                    });
                    this._addToolMessage(toolMessage);
                }
            } else if(item.type === "USER") {
                this._addUserMessage(item.message);
            } else if(item.type === "SYSTEM") {
                this._addSystemMessage(item.message);
            } else if (item.type === "TOOL_EXECUTION_RESULT"){
                this._addToolMessage(`Tool execution result for request ID = ${item.toolExecutionResult.id},
tool name = ${item.toolExecutionResult.toolName},
status = ${item.toolExecutionResult.text}`);
            }
        });
    }

    _filterChatItems(){
        this._chatItems = this._unfilteredChatItems.filter((item) => {
            if(item.userName === "Me" || item.userName === "AI" || item.userName === "Error"){
                return true;
            }else if(this._showToolRelatedMessages && item.userName === "Tools"){
                return true;
            }else if(item.userName === "System"){
                return true;
            }
            return false;
        });
    }

    _addToolMessage(message){
        this._addStyledMessage(message, "Tools", 9, "toolMessage");
    }

    _addErrorMessage(message){
        this._addStyledMessage(message, "Error", 7, "errorMessage");
    }

    _addSystemMessage(message){
        this._addStyledMessage(message, "System", 5, "systemMessage");
    }

    _addBotMessage(message){
        this._addMessage(message, "AI", 3);
    }

    _addUserMessage(message){
        this._addMessage(message, "Me", 1);
    }

    _addStyledMessage(message, user, colorIndex, className){
        let newItem = this._createNewItem(message, user, colorIndex);
        newItem.className = className;
        this._addMessageItem(newItem);
    }

    _addMessage(message, user, colorIndex){
        this._addMessageItem(this._createNewItem(message, user, colorIndex));
    }

    _createNewItem(message, user, colorIndex) {
        return {
            text: message,
            // FIXME: figure out how to store the correct timestamp
            // for each message? This is hard because we retrieve
            // the messages from the ChatMemory, which doesn't support
            // storing additional metadata with messages
            // time: new Date().toLocaleString(),
            userName: user,
            userColorIndex: colorIndex,
          };
    }

    _clearHistory() {
        this._chatItems = [];
        this._unfilteredChatItems = [];
    }

    _addMessageItem(newItem) {
        this._unfilteredChatItems = [
            ...this._unfilteredChatItems,
            newItem];
    }

    _hideNewConversationButton(){
        this._newConversationButtonClass = "remove";
    }

    _showNewConversationButton(){
        this._newConversationButtonClass = "add";
    }

    _hideProgressBar(){
        this._progressBarClass = "hide";
    }

    _showProgressBar(){
        this._progressBarClass = "show";
    }

    _enableSystemMessageInputField(){
        this._systemMessageInputFieldDisabled = null;
    }

    _disableSystemMessageInputField(){
        this._systemMessageInputFieldDisabled = "disabled";
    }

    _sampleSystemMessages = [
        {
            label: "",
            value: {
                systemMessage: "",
                userMessage: ""
            }
        },
        {
            label: "Summarize git diff",
            value: {
                systemMessage: "# IDENTITY and PURPOSE\n" +
                    "\n" +
                    "You are an expert project manager and developer, and you specialize in creating super clean updates for what changed in a Git diff.\n" +
                    "\n" +
                    "# STEPS\n" +
                    "\n" +
                    "- Read the input and figure out what the major changes and upgrades were that happened.\n" +
                    "\n" +
                    "- Create a section called CHANGES with a set of 7-10 word bullets that describe the feature changes and updates.\n" +
                    "\n" +
                    "- If there are a lot of changes include more bullets. If there are only a few changes, be more terse.\n" +
                    "\n" +
                    "# OUTPUT INSTRUCTIONS\n" +
                    "\n" +
                    "- Output a maximum 100 character intro sentence that says something like, \"chore: refactored the `foobar` method to support new 'update' arg\"\n" +
                    "\n" +
                    "- Use conventional commits - i.e. prefix the commit title with \"chore:\" (if it's a minor change like refactoring or linting), \"feat:\" (if it's a new feature), \"fix:\" if its a bug fix\n" +
                    "\n" +
                    "- You only output human readable Markdown, except for the links, which should be in HTML format.\n",
                userMessage: "diff --git a/samples/jbang-joke-bot/README.md b/samples/jbang-joke-bot/README.md\n" +
                    "new file mode 100644\n" +
                    "index 00000000..45435977\n" +
                    "--- /dev/null\n" +
                    "+++ b/samples/jbang-joke-bot/README.md\n" +
                    "@@ -0,0 +1,30 @@\n" +
                    "+# JBang sample aka getting an AI-powered bot in 13 lines of Java\n" +
                    "+\n" +
                    "+To run the sample, you need JBang installed. If you don't have it, choose\n" +
                    "+one of the installation options from the [JBang\n" +
                    "+website](https://www.jbang.dev/download/).\n" +
                    "+\n" +
                    "+You also have to set your OpenAI API key:\n" +
                    "+\n" +
                    "+```\n" +
                    "+export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>\n" +
                    "+```\n" +
                    "+\n" +
                    "+Then, run the example with:\n" +
                    "+\n" +
                    "+```\n" +
                    "+jbang jokes.java\n" +
                    "+```\n" +
                    "+\n" +
                    "+To have it tell you a joke, call `http://localhost:8080/joke` with a GET\n" +
                    "+request.\n" +
                    "+\n" +
                    "+Explanation: The code contains a single method which injects a\n" +
                    "+`io.vertx.ext.web.Router`, which is a class from the `quarkus-vertx-http`\n" +
                    "+extension responsible for routing requests to appropriate handlers. The\n" +
                    "+method is called during application boot thanks to the `@Observes`\n" +
                    "+annotation, and it uses the injected `Router` to define a single route on\n" +
                    "+the `/joke` path. The route's handler (the lambda expression that accepts a `rc` -\n" +
                    "+`RoutingContext` parameter) invokes the model and sets the HTTP response.\n" +
                    "+See [Quarkus documentation](https://quarkus.io/guides/reactive-routes#using-the-vert-x-web-router)\n" +
                    "+for more information.\n" +
                    "\\ No newline at end of file\n" +
                    "diff --git a/samples/jbang-joke-bot/jokes.java b/samples/jbang-joke-bot/jokes.java\n" +
                    "new file mode 100755\n" +
                    "index 00000000..51b02913\n" +
                    "--- /dev/null\n" +
                    "+++ b/samples/jbang-joke-bot/jokes.java\n" +
                    "@@ -0,0 +1,13 @@\n" +
                    "+//DEPS io.quarkus.platform:quarkus-bom:3.9.4@pom\n" +
                    "+//DEPS io.quarkus:quarkus-vertx-http:3.9.4\n" +
                    "+//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:0.12.0\n" +
                    "+\n" +
                    "+import dev.langchain4j.model.chat.ChatLanguageModel;\n" +
                    "+import io.vertx.ext.web.Router;\n" +
                    "+import jakarta.enterprise.event.Observes;\n" +
                    "+\n" +
                    "+public class jokes {\n" +
                    "+    void route(@Observes Router router, ChatLanguageModel ai) {\n" +
                    "+        router.get(\"/joke\").blockingHandler(rc -> rc.end(ai.generate(\"tell me a joke\")));\n" +
                    "+    }\n" +
                    "+}\n" +
                    "\\ No newline at end of file"
            }
        },
        // TODO: add more sample system+user messages
    ]

}

customElements.define('qwc-chat', QwcChat);
