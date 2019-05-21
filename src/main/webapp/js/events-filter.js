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
    var defaultFilterList = document.getElementById("defaultFilter").value.slice(1, -1).split(", ");
    var filterInList = document.getElementById("filterInForm").value.slice(1, -1).split(", ");
    var filterInForm = document.getElementById("filterInForm");
    var filterInSelect = document.getElementById("filterInSelect");
    var filterOutSelect = document.getElementById("filterOutSelect");
    var transfer = document.getElementById("transfer");
    var reset = document.getElementById("reset");
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

    //Fill the select lists based on filter array.
    //Any event that is filtered in will be removed from the full list of events
    //and the remaining events will be listed as filtered out.
    //If an event exists in the filter that does not exist in the full list it will not be displayed.
    function fillFilters(filter) {
        if (filter !== null) {
            var allEventsList = document.getElementById("allEvents").value.slice(1, -1).split(", ");
            filterInSelect.innerHTML = "";
            if (filter.length > 0 && filter[0] !== "") {
                var search;
                for (i = 0; i < filter.length; i++) {
                    search = filter[i];
                    if (allEventsList.indexOf(search) !== -1) {
                        allEventsList = removeFromArray(allEventsList, search);
                        filterInSelect.add(new Option(filter[i], filter[i], false, false));
                    }
                }
            }
            filterOutSelect.innerHTML = "";
            if (allEventsList.length > 0 && allEventsList[0] !== "") {
                for (i = 0; i < allEventsList.length; i++) {
                    filterOutSelect.add(new Option(allEventsList[i], allEventsList[i], false, false));
                }
            }
        }
    }
    fillFilters(filterInList);

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

    //Reset filter to default values
    reset.addEventListener('click', function () {
        fillFilters(defaultFilterList);
    });

    //Prepare form data when save button is clicked.
    //If the filter matches the default then nothing will be returned.
    //This leads to all events being set to their default values in the gerrit trigger.
    //If the filter differs from the default then the filter list will be prepared.
    save.addEventListener('click', function (e) {
        var filterIn = [];
        for (i = 0; i < filterInSelect.options.length; i++) {
            filterIn.push(filterInSelect.options[i].value);
        }
        if (!filterIn.equals(defaultFilterList)) {
            var filterInString = "";
            for (i = 0; i < filterIn.length; i++) {
                filterInString = filterInString + filterIn[i];
                if (filterIn.length - 1 !== i) {
                    filterInString = filterInString + " ";
                }
            }
            filterInForm.value = filterInString;
        } else {
            filterInForm.value = "null";
        }
    }, true);

    // tomáš-zato @ Stack Overflow
    // Compares two arrays with eachother
    if (Array.prototype.equals) {
        console.warn("Overriding existing Array.prototype.equals. Possible causes: New API defines the method, there's a framework conflict or you've got double inclusions in your code.");
    }
    Array.prototype.equals = function (array) {
        if (!array) {
            return false;
        }
        if (this.length !== array.length) {
            return false;
        }
        for (i = 0; i < this.length; i++) {
            if (this[i] instanceof Array && array[i] instanceof Array) {
                if (!this[i].equals(array[i])) {
                    return false;
                }
            } else if (this[i] !== array[i]) {
                return false;
            }
        }
        return true;
    };
});