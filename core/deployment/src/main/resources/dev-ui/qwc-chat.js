import {html, LitElement} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/text-area';
import '@vaadin/button';
import { JsonRpc } from 'jsonrpc';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

export class QwcChat extends LitElement {

    jsonRpc = new JsonRpc(this);

    static properties = {
        "_chatHistory": {state: true},
        "_errorMessage": {state: true}
    }

    constructor() {
        super();
        this._chatHistory = [];
    }

    render() {
        return html`
            <h3>Chat</h3>
            <vaadin-text-area id="system-message" label="(Optional) System message. To apply
a new system message, you have to use the New conversation button." style="width:90%"></vaadin-text-area><br/>
            <vaadin-text-area id="chat-message" label="Chat message" style="width:90%"></vaadin-text-area><br/>
            <vaadin-button id="chat-button" @click=${() => this._doChat(
                    this.shadowRoot.getElementById('chat-message').value
            )}>Submit
            </vaadin-button>
            <br/>
            ${this._errorMessage}
            <vaadin-button @click=${() => this._reset()}>New conversation</vaadin-button>
            <br/>
            ${this._renderHistory()}
        `;
    }

    _doChat(message) {
        // if no chat history exists, start a new conversation
        this.shadowRoot.getElementById('chat-message').value = "";
        this._setSubmitButtonEnabled(false);
        this._errorMessage = html``;
        if(this._chatHistory.length === 0) {
            var systemMessage = this.shadowRoot.getElementById('system-message').value;
            if(systemMessage) {
                this._chatHistory = [{message: message, type:"USER"}, {type: "SYSTEM", message: systemMessage}];
            } else {
                // don't show system message if empty
                this._chatHistory = [{message: message, type:"USER"}];
            }
            this.requestUpdate();
            this.jsonRpc.newConversation({message: message, systemMessage: systemMessage}).then(jsonRpcResponse => {
                this._showResponse(jsonRpcResponse);
            }).catch((error) => {
                this._chatHistory = this._chatHistory.slice(1);
                this._setSubmitButtonEnabled(true);
                this._showError(error);
            });
        } else {
            this._chatHistory = [{message: message, type: "USER"}].concat(this._chatHistory);
            this.requestUpdate();
            this.jsonRpc.chat({message: message}).then(jsonRpcResponse => {
                this._showResponse(jsonRpcResponse);
            }).catch((error) => {
                this._chatHistory = this._chatHistory.slice(1);
                this._setSubmitButtonEnabled(true);
                this._showError(error);
            });
        }
    }

    _showResponse(jsonRpcResponse) {
        if (jsonRpcResponse.result === false) {
            // the JsonRPC method threw an exception, this should generally
            // not happen, but just in case...
            this._chatHistory = this._chatHistory.slice(1);
            this._showError(jsonRpcResponse);
        } else {
            if (jsonRpcResponse.result.error) {
                this._chatHistory = this._chatHistory.slice(1);
                this._showError(jsonRpcResponse.result.error);
            } else {
                this._errorMessage = html``;
                this._chatHistory = jsonRpcResponse.result.history;
            }
        }
        this._setSubmitButtonEnabled(true);
        this.requestUpdate();
    }

    _showError(error) {
        var errorString = JSON.stringify(error);
        if(errorString === '{}') {
            // assume the error is a string
            errorString = error;
        }
        this._errorMessage = html`
                    <qui-alert level="error" showIcon>
                        <span>Error: ${errorString}</span>
                    </qui-alert>`;
    }

    _setSubmitButtonEnabled(value) {
        if(value) {
            this.shadowRoot.getElementById('chat-button').disabled = null;
        } else {
            this.shadowRoot.getElementById('chat-button').disabled = true;
        }
    }

    _reset() {
        var systemMessage = this.shadowRoot.getElementById('system-message').value;
        if(systemMessage) {
            this._chatHistory = [{type: "System", message: systemMessage}];
        } else {
            this._chatHistory = [];
        }
        this._setSubmitButtonEnabled(true);
        this.jsonRpc.reset({systemMessage: systemMessage});
    }

    _renderHistory() {
        return html`
                <vaadin-grid .items="${this._chatHistory}" theme="wrap-cell-content">
                    <vaadin-grid-column width="10ch" resizable flex-grow="0"
                                        path="type"
                                        header="Type">
                    </vaadin-grid-column>
                    <vaadin-grid-column path="message" 
                                        header="Message">
                    </vaadin-grid-column>
                </vaadin-grid>`;
    }

}

customElements.define('qwc-chat', QwcChat);