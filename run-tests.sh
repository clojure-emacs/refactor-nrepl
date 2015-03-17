refactor_nrepl=lein2 with-profile +1.5 test && \
    lein2 with-profile +1.6 test && \
    lein2 with-profile +1.7 test

cd refactor-nrepl-core;

refactor_nrepl_core=lein2 with-profile +1.5 test && \
    lein2 with-profile +1.6 test && \
    lein2 with-profile +1.7 test

return $refactor_nrepl && $refactor_nrepl_core
