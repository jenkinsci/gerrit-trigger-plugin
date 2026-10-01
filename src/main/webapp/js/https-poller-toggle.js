(function() {
    var cb = document.querySelector('input[name="useHttpsPoller"][type="checkbox"]');
    if (!cb) return;
    
    var sections = document.querySelectorAll('.ssh-only-section');
    var testButton = document.querySelector('.ssh-only-test-button');
    
    function toggle() {
        var hide = cb.checked;
        for (var i = 0; i < sections.length; i++) {
            sections[i].style.display = hide ? 'none' : '';
        }
        if (testButton) {
            testButton.style.display = hide ? 'none' : '';
        }
    }
    
    cb.addEventListener('change', toggle);
    toggle();
})();
