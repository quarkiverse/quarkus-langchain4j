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
      const response = JSON.parse(event.data);
      let markdownMessage = markdown.renderInline(response.message);
      if (response.links) {
        markdownMessage += `<ul>`;
        response.links.forEach(link => {
          markdownMessage += `<li><a href="${link.url}" target="_blank">${link.title}</a></li>`;
        });
        markdownMessage += `</ul>`;
      }

      chatBot.sendMessage(null, {
        message: markdownMessage,
        right: false,
        sender: {name: 'Bob', id: '007'}
      });
    }

    chatBot.addEventListener("sent", function (e) {
      if (e.detail.message.right === true) {
        // User message
        const userMessage = e.detail.message.message.replace(/^<p>(.*)<\/p>$/, '$1');
        socket.send(userMessage);
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
