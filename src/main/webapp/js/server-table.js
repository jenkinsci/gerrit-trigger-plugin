/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne a.k.a. rin_ne. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

function refreshServerTable() {
    const table = document.getElementById("gerrit-server-table");

    function generateSVGIcon(name) {
        const icons = document.getElementById("gerrit-svg-icons");
        return icons.content.querySelector(`#${name}`).cloneNode(true);
    }

    function createLink(url, icon, tagName="a", callback=null, disabled=false) {
        const td = document.createElement("td");
        td.classList.add("gt-table--center");

        const element = document.createElement(tagName);
        element.classList.add("jenkins-button");
        element.appendChild(icon);
        if (callback) {
            element.onclick = function(event) {
                callback(event, element);
            }
            element.disabled = disabled;
        } else {
            element.href = url;
        }
        td.appendChild(element);
        return td;
    }

    function toggleServer(event, element) {
        event.preventDefault();
        const row = element.closest("tr");
        let serverUrl = row.dataset.serverUrl;
        const status = row.dataset.status;
        let statusIcon;

        if (status === "up") {
            statusIcon = generateSVGIcon("gerrit-status-disconnecting");
            serverUrl +="/sleep";
        } else if(status == "down") {
            statusIcon = generateSVGIcon("gerrit-status-connecting");
            serverUrl +="/wakeup";
        }
        element.replaceChild(statusIcon, element.firstChild);
        fetch(serverUrl, {
            method: "post",
            headers: crumb.wrap({}),
        }).then(function(rsp) {
            if (rsp.ok) {
                setTimeout(refreshServerTable, 2000)
            }
        });
    }

    function createTableRow(server, tbody) {
        const row = document.createElement("tr");
        row.setAttribute("data-server-url", server.serverUrl);
        row.setAttribute("data-status", server.status);
        const nameTd = document.createElement("td");
        if (server.frontEndUrl !== '') {
            const a = document.createElement("a");
            a.href = server.frontEndUrl;
            a.target = "_blank";
            a.classList.add("gt-external-link");
            a.appendChild(document.createTextNode(server.name));
            a.appendChild(generateSVGIcon("gerrit-symbol-link"));
            nameTd.appendChild(a);
        } else {
            nameTd.innerHTML = server.name;
        }
        row.appendChild(nameTd);
        const versionTd = document.createElement("td");
        versionTd.innerText = server.version;
        row.appendChild(versionTd);

        let statusIcon;
        let disabled = false;
        let callback = "toggleServer";
        if (server.status === "up") {
            statusIcon= generateSVGIcon("gerrit-status-connected");
        } else if (server.status === "down") {
            statusIcon= generateSVGIcon("gerrit-status-disconnected")
        } else {
            statusIcon= generateSVGIcon("gerrit-status-unknown")
            disabled = true;
            callback = null;
        }
        row.appendChild(createLink("", statusIcon, "button", toggleServer, disabled));

        const icon = generateSVGIcon("gerrit-symbol-settings");
        if (server.hasErrors) {
            icon.classList.add("jenkins-!-error-color");
        } else if (server.hasWarnings) {
            icon.classList.add("jenkins-!-warning-color");
        }
        row.appendChild(createLink(`${server.serverUrl}/`, icon));

        row.appendChild(createLink(`${server.serverUrl}/remove`, generateSVGIcon("gerrit-symbol-remove")));
        tbody.appendChild(row);
    }

    fetch("serverStatuses").then(function(rsp) {
        if (rsp.ok) {
            rsp.json().then(function(json) {
                if (json.hasOwnProperty("servers") && json.servers.length > 0) {
                    table.classList.remove("jenkins-hidden");
                }
                const tbody = table.createTBody();
                json.servers.forEach(function(server) {
                    createTableRow(server, tbody)
                });
                table.tBodies[0].remove();
                tbody.classList.remove("jenkins-hidden");
            });
        }
    });
}

document.addEventListener("DOMContentLoaded", function() {
    refreshServerTable();
    setInterval(refreshServerTable, 30000);
});
