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

function setSelectedIds(ids) {
    'use strict';
    var input = document.getElementById('selectedIds');
    if (input !== null) {
        input.value = ids;
    }
}

function buildSelectedIds() {
    'use strict';
    var checkboxes = document.getElementsByName("selectedRow");
    var ids = "";
    var i, check;
    for (i = 0; i < checkboxes.length; i++) {
        check = checkboxes[i];
        if (check.checked) {
            ids += check.value + "[]";
        }
    }
    return ids;
}

function activateRow(theId) {
    'use strict';
    var radio = document.getElementById('check' + theId);
    if (radio !== null) {
        radio.click();
    } else {
        alert("No checkbox with id: " + theId);
    }
}

function rowSelected(theId) {
    'use strict';
    var radio = document.getElementById('check' + theId);
    if (radio !== null) {
        var row = document.getElementById('row' + theId);
        if (row !== null) {
            if (radio.checked === true) {
                row.style.fontWeight = "bold";
            } else {
                row.style.fontWeight = "normal";
            }
        }
    } else {
        alert("No checkbox with id: " + theId);
    }
    setSelectedIds(buildSelectedIds());
}

function toggleDetails(theId, collapsedImg, expandedImg) {
    'use strict';
    var img = document.getElementById('toggleImg' + theId);
    var details = document.getElementById('rowDetails' + theId);
    if (img !== null && details !== null) {
        if (details.style.display !== '') {
            details.style.display = '';
            img.src = expandedImg;
        } else {
            details.style.display = 'none';
            img.src = collapsedImg;
        }
    }
}
