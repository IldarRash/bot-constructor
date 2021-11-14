import React from 'react'
import CIcon from '@coreui/icons-react'
import {cilChartPie, cilPuzzle, cilSpeedometer,} from '@coreui/icons'
import {CNavItem, CNavTitle} from '@coreui/react'

const _nav = [
  {
    component: CNavItem,
    name: 'Dashboard',
    to: '/dashboard',
    icon: <CIcon icon={cilSpeedometer} customClassName="nav-icon" />,
    badge: {
      color: 'info',
      text: 'NEW',
    },
  },
  {
    component: CNavTitle,
    name: 'Основные функций',
  },
  {
    component: CNavItem,
    name: 'Конструктор',
    icon: <CIcon icon={cilPuzzle} customClassName="nav-icon" />,
    to: '/bot/board',
    badge: {
      color: 'success',
      text: 'NEW',
    },
  },
  {
    component: CNavItem,
    name: 'Интеграций с crm',
    to: '/charts',
    icon: <CIcon icon={cilChartPie} customClassName="nav-icon" />,
  },
  {
    component: CNavItem,
    name: 'Login',
    to: '/login',
  },
  {
    component: CNavItem,
    name: 'Register',
    to: '/register',
  },
  {
    component: CNavItem,
    name: 'Error 404',
    to: '/404',
  },
  {
    component: CNavItem,
    name: 'Error 500',
    to: '/500',
  },
]

export default _nav
