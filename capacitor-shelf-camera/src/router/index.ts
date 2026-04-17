import { createRouter, createWebHistory } from 'vue-router';
import HomePage from '../views/Home/HomePage.vue';

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'home',
            component: HomePage,
        },
        {
            path: '/capture',
            name: 'capture',
            component: () => import('../views/ShelfCapture/ShelfCapturePage.vue'),
            meta: { transition: 'slide-left' },
        },
    ],
});

export default router;
