import {css, LitElement} from 'lit';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/form-layout';
import '@vaadin/progress-bar';
import '@vaadin/checkbox';
import '@vaadin/horizontal-layout';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';

export class DemoChat extends LitElement {
    static styles = css`
      .button {
        cursor: pointer;
      }
    `;

    connectedCallback() {
        const chatBot = document.getElementsByTagName("chat-bot")[0];

        const socket = new WebSocket("ws://" + window.location.host + "/chatbot");
        socket.onmessage = function (event) {
            chatBot.sendMessage(event.data, {
                right: false,
                sender: {name: 'Bob', id: '007'}
            });
        }

        chatBot.addEventListener("sent", function (e) {
            if (e.detail.message.right === true) {
                // User message
                socket.send(e.detail.message.message);
                chatBot.sendMessage("", {
                    right: false,
                    sender: {name: 'Bob', id: '007'},
                    loading: true
                });
            }
        });
    }
}

customElements.define('demo-chat', DemoChat);