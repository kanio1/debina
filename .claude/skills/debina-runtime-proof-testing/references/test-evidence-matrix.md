# Evidence matrix

Choose tests by changed boundary: migrations need fresh/upgrade/roles/grants; RLS needs tenant/GUC/pool cases; transactions need txid/rollback/crash/replay; messaging needs broker/duplicate/retry; critical completed slices need two sequential full regressions.
