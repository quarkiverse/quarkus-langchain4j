import {LitElement} from 'lit';
import MarkdownIt from 'markdown-it';

export class DemoChat extends LitElement {

  constructor() {
    super();
    this.md = new MarkdownIt({
      breaks: true,
      linkify: true,
      xhtmlOut: false,
      html: false,
      typographer: true,
    })
  }

  connectedCallback() {
    const chatBot = document.getElementsByTagName("chat-bot")[0];
    const markdown = this.md;

    const socket = new WebSocket("ws://" + window.location.host + "/chatbot");
    socket.onmessage = function (event) {
      chatBot.hideAllLoading();
      // Render markdown from event.data
      const renderedMarkdown = markdown.renderInline(event.data);
      chatBot.sendMessage(null, {
        message: renderedMarkdown,
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
