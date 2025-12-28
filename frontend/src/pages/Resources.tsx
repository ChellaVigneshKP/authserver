import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Paper,
  Typography,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
} from '@mui/icons-material';
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid';
import { resourceService } from '../../services/resourceService';
import type { ResourceLibrary } from '../../types/index';
import { Loading } from '../common/Loading';
import { ErrorDisplay } from '../common/ErrorDisplay';

export const Resources = () => {
  const [resources, setResources] = useState<ResourceLibrary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openDialog, setOpenDialog] = useState(false);
  const [selectedResource, setSelectedResource] = useState<ResourceLibrary | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    endpoint: '',
    method: 'GET',
  });
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    loadResources();
  }, []);

  const loadResources = async () => {
    try {
      setLoading(true);
      const data = await resourceService.getAll();
      setResources(data);
    } catch (err) {
      setError('Failed to load resources');
      console.error('Load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenDialog = (resource?: ResourceLibrary) => {
    if (resource) {
      setSelectedResource(resource);
      setFormData({
        name: resource.name,
        description: resource.description || '',
        endpoint: resource.endpoint || '',
        method: resource.method || 'GET',
      });
    } else {
      setSelectedResource(null);
      setFormData({ name: '', description: '', endpoint: '', method: 'GET' });
    }
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setSelectedResource(null);
  };

  const handleSubmit = async () => {
    try {
      if (selectedResource) {
        await resourceService.update(selectedResource.id, formData);
        setSuccessMessage('Resource updated successfully');
      } else {
        await resourceService.create(formData);
        setSuccessMessage('Resource created successfully');
      }
      handleCloseDialog();
      loadResources();
      setTimeout(() => setSuccessMessage(''), 3000);
    } catch (err) {
      setError('Failed to save resource');
      console.error('Save error:', err);
    }
  };

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 200 },
    { field: 'description', headerName: 'Description', flex: 2, minWidth: 300 },
    { field: 'endpoint', headerName: 'Endpoint', flex: 1.5, minWidth: 250 },
    { field: 'method', headerName: 'Method', flex: 0.5, minWidth: 100 },
    {
      field: 'actions',
      headerName: 'Actions',
      flex: 0.5,
      minWidth: 100,
      sortable: false,
      renderCell: (params: GridRenderCellParams) => (
        <IconButton
          size="small"
          color="primary"
          onClick={() => handleOpenDialog(params.row as ResourceLibrary)}
          title="Edit"
        >
          <EditIcon />
        </IconButton>
      ),
    },
  ];

  if (loading) return <Loading message="Loading resources..." />;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Resource Library</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => handleOpenDialog()}
        >
          Create Resource
        </Button>
      </Box>

      {successMessage && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {successMessage}
        </Alert>
      )}

      {error && <ErrorDisplay error={error} />}

      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={resources}
          columns={columns}
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: { paginationModel: { pageSize: 10 } },
          }}
          disableRowSelectionOnClick
        />
      </Paper>

      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          {selectedResource ? 'Edit Resource' : 'Create Resource'}
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Resource Name"
            type="text"
            fullWidth
            required
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            sx={{ mt: 2 }}
          />
          <TextField
            margin="dense"
            label="Description"
            type="text"
            fullWidth
            multiline
            rows={2}
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            sx={{ mt: 2 }}
          />
          <TextField
            margin="dense"
            label="Endpoint"
            type="text"
            fullWidth
            value={formData.endpoint}
            onChange={(e) => setFormData({ ...formData, endpoint: e.target.value })}
            sx={{ mt: 2 }}
            placeholder="/api/v1/resource"
          />
          <TextField
            margin="dense"
            label="HTTP Method"
            type="text"
            fullWidth
            value={formData.method}
            onChange={(e) => setFormData({ ...formData, method: e.target.value })}
            sx={{ mt: 2 }}
            placeholder="GET, POST, PUT, DELETE"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button onClick={handleSubmit} variant="contained" disabled={!formData.name}>
            {selectedResource ? 'Update' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
