import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 5_000, refetchOnWindowFocus: true, retry: 1 } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <Toaster
        position="bottom-right"
        theme="system"
        toastOptions={{
          className: '!bg-elev !border-line-strong !text-fg !rounded-xl !shadow-soft',
          descriptionClassName: '!text-muted',
        }}
      />
    </QueryClientProvider>
  </StrictMode>,
)
