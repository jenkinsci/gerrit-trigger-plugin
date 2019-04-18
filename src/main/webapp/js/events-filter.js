/*
 *  The MIT License
 *
 *  Copyright 2019 Ericsson. All rights reserved.
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

document.addEventListener('DOMContentLoaded', function () {
    'use strict';
    var filterDefaultList = document.getElementById("filterDefault").value.slice(1, -1).split(", ");
    var filterInForm = document.getElementById("filterInForm").value.slice(1, -1).split(", ");
    var filterOutForm = document.getElementById("filterOutForm").value.slice(1, -1).split(", ");
    var filterInSelect = document.getElementById("filterInSelect");
    var filterOutSelect = document.getElementById("filterOutSelect");
    var transfer = document.getElementById("transfer");
    var save = document.getElementById("save");
    var i;

    //Fill the select lists based on form data.
    //If settings are opened for the first time, default list with all events will be displayed.
    if (filterInForm[0] === "" && filterOutForm[0] === "") {
        for (i = 0; i < filterDefaultList.length; i++) {
            filterInSelect.add(new Option(filterDefaultList[i], filterDefaultList[i], false, false));
        }
    } else {
        if (filterInForm.length > 0 && filterInForm[0] !== "") {
            filterInSelect.innerHTML = "";
            for (i = 0; i < filterInForm.length; i++) {
                filterInSelect.add(new Option(filterInForm[i], filterInForm[i], false, false));
            }
        }
        if (filterOutForm.length > 0 && filterOutForm[0] !== "") {
            filterOutSelect.innerHTML = "";
            for (i = 0; i < filterOutForm.length; i++) {
                filterOutSelect.add(new Option(filterOutForm[i], filterOutForm[i], false, false));
            }
        }
    }

    //Get values that have been selected in the list.
    function getSelectValues(select) {
        var result = [];
        var options = select.options;
        var option;

        for (i = 0; i < options.length; i++) {
            option = options[i];
            if (option.selected) {
                result.push(option.value);
            }
        }
        return result;
    }

    //Transfer values from one list to the other.
    function transferToFilter(filterFrom, filterTo) {
        var selected = getSelectValues(filterFrom);
        for (i = 0; i < selected.length; i++) {
            filterTo.add(new Option(selected[i], selected[i], false, false));
            filterFrom.remove(filterFrom.selectedIndex);
        }
    }

    //Sort list items in alphabetical order.
    function sortSelect(select) {
        if (select.options.length > 1) {
            var tempArray = [];
            for (i = 0; i < select.options.length; i++) {
                tempArray[i] = [];
                tempArray[i][0] = select.options[i].text;
                tempArray[i][1] = select.options[i].value;
            }
            tempArray.sort();
            while (select.options.length > 0) {
                select.options[0] = null;
            }
            for (i = 0; i < tempArray.length; i++) {
                select.options[i] = new Option(tempArray[i][0], tempArray[i][1]);
            }
            return;
        }
    }

    //Prevent items from being selected in both lists at once.
    filterInSelect.addEventListener('change', function () {
        filterOutSelect.value = undefined;
    });
    filterOutSelect.addEventListener('change', function () {
        filterInSelect.value = undefined;
    });
    //Transfer selections and sort lists when button is clicked.
    transfer.addEventListener('click', function () {
        transferToFilter(filterInSelect, filterOutSelect);
        transferToFilter(filterOutSelect, filterInSelect);
        sortSelect(filterInSelect);
        sortSelect(filterOutSelect);
    });
    //Prepare form data when save button is clicked.
    save.addEventListener('click', function (e) {
        e.stopImmediatePropagation();
        var filterInString = "[";
        var filterOutString = "[";
        for (i = 0; i < filterInSelect.options.length; i++) {
            filterInString = filterInString + filterInSelect.options[i].value;
            if (filterInSelect.options.length - 1 !== i) {
                filterInString = filterInString + ", ";
            }
        }
        filterInString = filterInString + "]";
        for (i = 0; i < filterOutSelect.options.length; i++) {
            filterOutString = filterOutString + filterOutSelect.options[i].value;
            if (filterOutSelect.options.length - 1 !== i) {
                filterOutString = filterOutString + ", ";
            }
        }
        filterOutString = filterOutString + "]";
        document.getElementById("filterInForm").value = filterInString;
        document.getElementById("filterOutForm").value = filterOutString;
    }, true);
});