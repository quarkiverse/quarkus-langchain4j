import {LitElement, html, css} from 'lit';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/details';
import '@vaadin/vertical-layout';
import '@vaadin/message-input';
import '@vaadin/message-list';
import '@vaadin/progress-bar';
import '@vaadin/text-field';
import '@vaadin/icon';
import '@vaadin/icons';
import '@vaadin/tooltip';
import { JsonRpc } from 'jsonrpc';

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
        _chatItems: {state: true},
        _progressBarClass: {state: true},
        _newConversationButtonClass: {state: true},
        _systemMessage: {state: true},
        _systemMessageDisabled: {state: true}
    }

    constructor() {
        super();
        this._hideProgressBar();
        this._startNewConversation();
        
        this._chatItems = [];
    }

    render() {
        return html`
            ${this._renderSystemPane()}
            <vaadin-message-list .items="${this._chatItems}"></vaadin-message-list>
            <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
            <vaadin-message-input @submit="${this._handleSendChat}"></vaadin-message-input>
        `;
    }

    _renderSystemPane(){
        return html`<div class="systemMessagePane">
            <vaadin-button class="${this._newConversationButtonClass}" @click="${this._startNewConversation}">Start a new conversation</vaadin-button>
            <vaadin-text-field class="systemMessageInput"
                    placeholder="(Optional). Changing this will start a new converation"
                    @keypress="${this._checkForEnterOrTab}" 
                    @focusout="${this._checkForEnterOrTab}"
                    @input="${this._populateSystemMessage}" 
                    value="${this._systemMessage}" 
                    ?disabled=${this._systemMessageDisabled}>
                    <span slot="prefix">System message: </span> 
            </vaadin-text-field>
            </div>`;
    }

    _checkForEnterOrTab(e){
        if ((e.which == 13 || e.which == 0)){
            if(this._systemMessage && this._systemMessage.trim().length > 0)
            this._cementSystemMessage();
            this.shadowRoot.querySelector('.systemMessageInput').focus();
        }
    }

    _populateSystemMessage(e){
        if(e.target.value.trim() === ''){
            this._startNewConversation();
        }else{
            this._systemMessage = e.target.value;
        }
    }

    _startNewConversation(){
        this._enableSystemMessage();
        this._hideNewConversationButton();
        this._systemMessage = null;
    }

    _cementSystemMessage(){
        if(!this._systemMessageDisabled){
            this._disableSystemMessage();
            this._showNewConversationButton();
            this._addSystemMessage(this._systemMessage);
            this.jsonRpc.reset({systemMessage: this._systemMessage});
        }
    }

    _handleSendChat(e) {
        let message = e.detail.value;
        if(message && message.trim().length>0){
            this._cementSystemMessage();
            this._addUserMessage(message);
            this._showProgressBar();
            
            this.jsonRpc.newConversation({message: message, systemMessage: this._systemMessage}).then(jsonRpcResponse => {
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
                this._addBotMessage(jsonRpcResponse.result.history[0].message);
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
            time: new Date().toLocaleString(),
            userName: user,
            userColorIndex: colorIndex,
          };
    }

    _addMessageItem(newItem){
        this._chatItems = [
            ...this._chatItems,
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

    _enableSystemMessage(){
        this._systemMessageDisabled = null;
    }

    _disableSystemMessage(){
        this._systemMessageDisabled = "disabled";
    }

}

customElements.define('qwc-chat', QwcChat);