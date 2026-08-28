-- Fixed 7 extensions ship unchecked (is_blocked = false) per spec; an admin opts in.
INSERT INTO extension_policy (extension, type, is_blocked) VALUES
    ('bat', 'FIXED', FALSE),
    ('cmd', 'FIXED', FALSE),
    ('com', 'FIXED', FALSE),
    ('cpl', 'FIXED', FALSE),
    ('exe', 'FIXED', FALSE),
    ('scr', 'FIXED', FALSE),
    ('js',  'FIXED', FALSE);
