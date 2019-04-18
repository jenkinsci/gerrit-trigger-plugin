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
    var filterInList = document.getElementById("filterInForm").value.slice(1, -1).split(", ");
    var filterInForm = document.getElementById("filterInForm");
    var filterInSelect = document.getElementById("filterInSelect");
    var filterOutSelect = document.getElementById("filterOutSelect");
    var transfer = document.getElementById("transfer");
    var save = document.getElementById("save");
    var i;

    //Remove a value from an array.
    function removeFromArray(array, value) {
        return array.filter(function (e) { return e !== value; });
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

    //Fill the select lists based on form data.
    //If there is no form data, the default full list of events will be filtered in.
    //If there is form data, then any event that is filtered in will be removed from
    //the full list of events and the remaining events will be listed as filtered out.
    if (filterInForm.value === "") {
        for (i = 0; i < filterDefaultList.length; i++) {
            filterInSelect.add(new Option(filterDefaultList[i], filterDefaultList[i], false, false));
        }
    } else {
        if (filterInList.length > 0 && filterInList[0] !== "") {
            filterInSelect.innerHTML = "";
            var search;
            for (i = 0; i < filterInList.length; i++) {
                search = filterInList[i];
                if (filterDefaultList.includes(search)) {
                    filterDefaultList = removeFromArray(filterDefaultList, search);
                    filterInSelect.add(new Option(filterInList[i], filterInList[i], false, false));
                } else {
                    filterInList = removeFromArray(filterInList, search);
                    i--;
                }
            }
        }
        if (filterDefaultList.length > 0 && filterDefaultList[0] !== "") {
            filterOutSelect.innerHTML = "";
            for (i = 0; i < filterDefaultList.length; i++) {
                filterOutSelect.add(new Option(filterDefaultList[i], filterDefaultList[i], false, false));
            }
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
        if (filterOutSelect.options.length !== 0) {
            var filterInString = "[";
            for (i = 0; i < filterInSelect.options.length; i++) {
                filterInString = filterInString + filterInSelect.options[i].value;
                if (filterInSelect.options.length - 1 !== i) {
                    filterInString = filterInString + ", ";
                }
            }
            filterInString = filterInString + "]";
            document.getElementById("filterInForm").value = filterInString;
        } else {
            document.getElementById("filterInForm").value = "";
        }
    }, true);
});