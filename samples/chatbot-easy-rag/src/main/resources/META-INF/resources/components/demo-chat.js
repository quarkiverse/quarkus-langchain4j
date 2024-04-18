import {LitElement} from 'lit';

export class DemoChat extends LitElement {
    
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