/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

Behaviour.specify(".gt-search__activate", "gerrit-search", 0, function(element) {
    const row = element.closest("tr");
    const theId = row.id;

    element.onclick = function(event) {
        const radio = document.getElementById('check' + theId);
        if (radio !== null) {
            radio.click();
        } else {
            alert("No checkbox with id: " + theId);
        }
    };
});

Behaviour.specify(".gt-search__checkbox", "gerrit-search", 0, function(radio) {
    const row = radio.closest("tr");

    function setSelectedIds(ids) {
        const input = document.getElementById('selectedIds');
        if (input !== null) {
            input.value = ids;
        }
    }

    function buildSelectedIds() {
        const checkboxes = document.getElementsByName("selectedRow");
        let ids = "";
        let i, check;
        for (i = 0; i < checkboxes.length; i++) {
            check = checkboxes[i];
            if (check.checked) {
                ids += check.value + "[]";
            }
        }
        return ids;
    }

    radio.onclick = function(e) {
        if (radio.checked === true) {
            row.classList.add("gt-search__row--selected");
        } else {
            row.classList.remove("gt-search__row--selected");
        }
        setSelectedIds(buildSelectedIds());
    };
});

Behaviour.specify(".gt-search__details", "gerrit-search", 0, function(link) {
    const row = link.closest("tr");
    const theId = row.id;

    link.onclick = function (e) {
        e.preventDefault();
        const details = document.getElementById('rowDetails' + theId);
        if (details !== null) {
            if (details.classList.contains("jenkins-hidden")) {
                details.classList.remove("jenkins-hidden");
                link.dataset.expanded = "true";
            } else {
                details.classList.add("jenkins-hidden");
                link.dataset.expanded = "false";
            }
        }
    };
});

