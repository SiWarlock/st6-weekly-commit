import { configureStore } from '@reduxjs/toolkit';
import { baseApi } from './baseApi';

/**
 * Redux store wired with the RTK Query base api reducer + middleware (cache
 * lifecycle, invalidation, polling). Domain slices inject endpoints into
 * `baseApi`, so no per-slice reducer registration is needed here.
 */
export const store = configureStore({
  reducer: {
    [baseApi.reducerPath]: baseApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(baseApi.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
