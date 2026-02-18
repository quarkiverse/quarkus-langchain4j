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
import '@vaadin/dialog';
import 'qui-alert';
import { JsonRpc } from 'jsonrpc';
import { systemMessages } from 'build-time-data';
import { dialogHeaderRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import { msg, updateWhenLocaleChanges } from 'localization';

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

        .header {
            display: flex;
            justify-content: flex-end;
            padding: var(--lumo-space-s);
            gap: var(--lumo-space-s);
        }

        .chatContainer {
            display: flex;
            flex-direction: column;
            flex: 1;
            min-height: 0;
        }

        .chatContainerEmpty {
            display: flex;
            flex-direction: column;
            flex: 1;
            min-height: 0;
            justify-content: center;
            align-items: center;
        }

        .emptyStateInput {
            width: 100%;
            max-width: 600px;
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

        vaadin-message-list {
            flex: 1;
            overflow: auto;
        }
    `;

    static properties = {
        _unfilteredChatItems: {state: true},
        _progressBarClass: {state: true},
        _systemMessage: {state: true},
        _systemMessages: {state: true},
        _ragEnabled: {state: true},
        _streamingChatSupported: {state: true},
        _streamingChatEnabled: {state: true},
        _showToolRelatedMessages: {state: true},
        _showSettingsDialog: {state: true},
        _observer: {state: false},
    }

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._showToolRelatedMessages = true;
        this._ragEnabled = true;
        this._systemMessages = systemMessages;
        this._systemMessage = systemMessages.length == 1 ? systemMessages[0] : "";
        this._progressBarClass = "hide";
        this._unfilteredChatItems = [];
        this._showSettingsDialog = false;
        this.jsonRpc.reset({systemMessage: this._systemMessage});
        this._streamingChatSupported = this.jsonRpc.isStreamingChatSupported();
        this._streamingChatEnabled = this._streamingChatSupported && !this._ragEnabled;
    }

    _connect() {
    }

    _disconnect() {
      if (this._observer) {
        this._observer.unsubscribe();
      }
    }

    connectedCallback() {
        super.connectedCallback();
        this._connect();
    }

    disconnectedCallback() {
        this._disconnect();
        super.disconnectedCallback();
    }

    get _chatItems() {
        return this._unfilteredChatItems.filter((item) => {
            if (item.userName === "Me" || item.userName === "AI" || item.userName === "Error") {
                return true;
            } else if (this._showToolRelatedMessages && item.userName === "Tools") {
                return true;
            } else if (item.userName === "System") {
                return true;
            }
            return false;
        });
    }

    get _hasMessages() {
        return this._unfilteredChatItems.length > 0;
    }

    render() {
        return html`
            ${this._renderSettingsDialog()}
            <div class="header">
                <vaadin-button theme="tertiary" @click="${this._startNewConversation}">
                    <vaadin-icon icon="vaadin:plus" slot="prefix"></vaadin-icon>
                    ${msg('New conversation', { id: 'chat-new-conversation' })}
                </vaadin-button>
                <vaadin-button theme="tertiary" @click="${() => this._showSettingsDialog = true}">
                    <vaadin-icon icon="vaadin:cog" slot="prefix"></vaadin-icon>
                    ${msg('Settings', { id: 'chat-settings' })}
                </vaadin-button>
            </div>
            ${this._hasMessages ? this._renderChatView() : this._renderEmptyState()}
        `;
    }

    _renderEmptyState() {
        return html`
            <div class="chatContainerEmpty">
                <vaadin-message-input class="emptyStateInput" @submit="${this._handleSendChat}"></vaadin-message-input>
            </div>
        `;
    }

    _renderChatView() {
        return html`
            <div class="chatContainer">
                <vaadin-message-list .items="${this._chatItems}"></vaadin-message-list>
                <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
                <vaadin-message-input @submit="${this._handleSendChat}"></vaadin-message-input>
            </div>
        `;
    }

    _renderSettingsDialog() {
        return html`<vaadin-dialog
            header-title="${msg('Chat Settings', { id: 'chat-settings-title' })}"
            resizable
            draggable
            .opened="${this._showSettingsDialog}"
            @opened-changed="${(e) => {
                this._showSettingsDialog = e.detail.value;
            }}"
            ${dialogHeaderRenderer(() => html`
                <vaadin-button theme="tertiary" @click="${() => { this._showSettingsDialog = false; }}">
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            `, [])}
            ${dialogRenderer(() => html`
                <vaadin-vertical-layout theme="spacing" style="width: 500px; max-width: 80vw;">
                    <vaadin-checkbox
                        ?checked="${this._showToolRelatedMessages}"
                        label="${msg('Show tool-related messages', { id: 'chat-show-tool-messages' })}"
                        @change="${(event) => {
                            this._showToolRelatedMessages = event.target.checked;
                            this.requestUpdate();
                        }}">
                    </vaadin-checkbox>

                    <vaadin-checkbox
                        ?checked="${this._ragEnabled}"
                        label="${msg('Enable Retrieval Augmented Generation (if a RetrievalAugmentor bean exists)', { id: 'chat-enable-rag' })}"
                        @change="${(event) => {
                            this._ragEnabled = event.target.checked;
                            this._streamingChatEnabled = this._streamingChatEnabled && !this._ragEnabled;
                        }}">
                    </vaadin-checkbox>

                    ${this._streamingChatSupported ? html`
                        <vaadin-checkbox
                            ?checked="${this._streamingChatEnabled}"
                            label="${msg('Enable Streaming Chat', { id: 'chat-enable-streaming' })}"
                            @change="${(event) => {
                                this._streamingChatEnabled = event.target.checked;
                            }}">
                        </vaadin-checkbox>
                    ` : ''}

                    <vaadin-text-field
                        style="width: 100%;"
                        label="${msg('System message', { id: 'chat-system-message' })}"
                        helper-text="${msg('Applied when starting a new conversation', { id: 'chat-system-message-helper' })}"
                        placeholder="${msg('Optional system message', { id: 'chat-system-message-placeholder' })}"
                        .value="${this._systemMessage}"
                        @input="${this._populateSystemMessage}">
                    </vaadin-text-field>
                </vaadin-vertical-layout>
            `, [this._showToolRelatedMessages, this._ragEnabled, this._streamingChatEnabled, this._systemMessage])}
        ></vaadin-dialog>`;
    }

    _populateSystemMessage(e) {
        if (e.target.value.trim() === '') {
            this._systemMessage = "";
        } else {
            this._systemMessage = e.target.value;
        }
    }

    _startNewConversation() {
        this._clearHistory();
        if (this._systemMessage && this._systemMessage.trim().length > 0) {
            this._addSystemMessage(this._systemMessage);
        }
        this.jsonRpc.reset({systemMessage: this._systemMessage});
    }

    _handleSendChat(e) {
        let message = e.detail.value;
        if (message && message.trim().length > 0) {
            var indexUserMessage = this._addUserMessage(message);
            this._showProgressBar();

            if (this._streamingChatEnabled) {
                var msg = "";
                var index = null;
                try {
                    this._observer = this.jsonRpc.streamingChat({message: message, ragEnabled: this._ragEnabled})
                        .onNext(jsonRpcResponse => {
                            if (jsonRpcResponse.result.error) {
                                this._showError(jsonRpcResponse.result.error);
                                this._hideProgressBar();
                            } else if (jsonRpcResponse.result.augmentedMessage) {
                                this._updateMessage(indexUserMessage, jsonRpcResponse.result.augmentedMessage);
                            } else if (jsonRpcResponse.result.toolExecutionRequest) {
                                var item = jsonRpcResponse.result.toolExecutionRequest;
                                this._addToolMessage(`Request to execute the following tool:
                                    Request ID = ${item.id},
                                    tool name = ${item.name},
                                    arguments = ${item.arguments}`);
                            } else if (jsonRpcResponse.result.toolExecutionResult) {
                                var item = jsonRpcResponse.result.toolExecutionResult;
                                this._addToolMessage(`Tool execution result for request ID = ${item.id},
                                    tool name = ${item.toolName},
                                    status = ${item.text}`);
                            } else if (jsonRpcResponse.result.message) {
                                this._updateMessage(index, jsonRpcResponse.result.message);
                                this._hideProgressBar();
                            } else {
                                if (index === null) {
                                    index = this._addBotMessage(msg);
                                }
                                msg += jsonRpcResponse.result.token;
                                this._updateMessage(index, msg);
                            }
                        })
                        .onError((error) => {
                            this._showError(error);
                            this._hideProgressBar();
                        });
                } catch (error) {
                    this._showError(error);
                    this._hideProgressBar();
                }
            } else {
                this.jsonRpc.chat({message: message, ragEnabled: this._ragEnabled}).then(jsonRpcResponse => {
                    this._showResponse(jsonRpcResponse);
                }).catch((error) => {
                    this._showError(error);
                    this._hideProgressBar();
                });
            }
        }
    }

    _showResponse(jsonRpcResponse) {
        if (jsonRpcResponse.result === false) {
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
        if (errorString === '{}') {
            errorString = error;
        }
        this._addErrorMessage(errorString);
    }

    _processResponse(items) {
        this._unfilteredChatItems = [];
        items.forEach((item) => {
            if (item.type === "AI") {
                if (item.message) {
                    this._addBotMessage(item.message);
                }
                if (item.toolExecutionRequests) {
                    var toolMessage = "Request to execute the following tools:\n";
                    item.toolExecutionRequests.forEach((toolExecutionRequest) => {
                        toolMessage += `Request ID = ${toolExecutionRequest.id},
tool name = ${toolExecutionRequest.name},
arguments = ${toolExecutionRequest.arguments}\n`;
                    });
                    this._addToolMessage(toolMessage);
                }
            } else if (item.type === "USER") {
                this._addUserMessage(item.message);
            } else if (item.type === "SYSTEM") {
                this._addSystemMessage(item.message);
            } else if (item.type === "TOOL_EXECUTION_RESULT") {
                this._addToolMessage(`Tool execution result for request ID = ${item.toolExecutionResult.id},
tool name = ${item.toolExecutionResult.toolName},
status = ${item.toolExecutionResult.text}`);
            }
        });
    }

    _addToolMessage(message) {
        this._addStyledMessage(message, "Tools", 9, "toolMessage");
    }

    _addErrorMessage(message) {
        this._addStyledMessage(message, "Error", 7, "errorMessage");
    }

    _addSystemMessage(message) {
        this._addStyledMessage(message, "System", 5, "systemMessage");
    }

    _addBotMessage(message) {
        return this._addMessage(message, "AI", 3);
    }

    _updateMessage(index, message) {
        this._unfilteredChatItems[index].text = message;
        this._unfilteredChatItems = [...this._unfilteredChatItems];
    }

    _addUserMessage(message) {
        return this._addMessage(message, "Me", 1);
    }

    _addStyledMessage(message, user, colorIndex, className) {
        let newItem = this._createNewItem(message, user, colorIndex);
        newItem.className = className;
        this._addMessageItem(newItem);
    }

    _addMessage(message, user, colorIndex) {
        return this._addMessageItem(this._createNewItem(message, user, colorIndex));
    }

    _createNewItem(message, user, colorIndex) {
        return {
            text: message,
            userName: user,
            userColorIndex: colorIndex,
        };
    }

    _clearHistory() {
        this._unfilteredChatItems = [];
    }

    _addMessageItem(newItem) {
        var newIndex = this._unfilteredChatItems.length;
        this._unfilteredChatItems = [
            ...this._unfilteredChatItems,
            newItem
        ];
        return newIndex;
    }

    _hideProgressBar() {
        this._progressBarClass = "hide";
    }

    _showProgressBar() {
        this._progressBarClass = "show";
    }
}

customElements.define('qwc-chat', QwcChat);
