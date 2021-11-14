import React from 'react'

const Dashboard = React.lazy(() => import('./components/pages/Dashboard'))
const BotBoard = React.lazy(() => import('./layout/main-container'))
const routes = [
  { path: '/', exact: true, name: 'Home' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/bot/board', name: 'Flow' , component: BotBoard }
]

export default routes
